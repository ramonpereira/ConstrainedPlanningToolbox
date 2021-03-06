/*******************************************************************************
 * ConstrainedPlanningToolbox
 * Copyright (C) 2019 Algorithmics group, Delft University of Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *******************************************************************************/
package domains.tcl.simpletcl.transition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import util.ConsoleOutput;


import domains.tcl.Action;
import domains.tcl.State;
import domains.tcl.primitives.ControlAction;
import domains.tcl.primitives.TemperatureFunction;
import domains.tcl.simpletcl.TCLAgent;
import domains.tcl.simpletcl.action.TCLAction;
import domains.tcl.simpletcl.action.TCLActionManager;
import domains.tcl.simpletcl.state.TCLState;
import domains.tcl.simpletcl.state.TCLStateManager;


public class TransitionFunction
{
	private static final long PRECISION = 10000000;

	// Functions over the simple elements.
	private Map<TCLState, Map<TCLAction, Map<TCLState, Double>>> fTransitionFunction;

	private TCLActionManager actions;
	private TCLStateManager  states;

	private final double outTemp;
	private final int    transitionDelta;

	public TransitionFunction(TCLAgent agent, TCLActionManager actions, TCLStateManager states, double pOutside, int pPlanningStep)
	{
		fTransitionFunction = new HashMap<TCLState, Map<TCLAction, Map<TCLState,Double>>>();

		this.actions         = actions;
		this.states          = states;
		this.outTemp         = pOutside;
		this.transitionDelta = pPlanningStep;

		computeTransitionFunction(agent);
	}

	public void computeTransitionFunction(TCLAgent agent)
	{
		final int maxID = (states.getNumStates()-1);

		double nextLow;
		double nextHigh;

		TCLState      nextState  = null;
		Set<TCLState> nextStates = new HashSet<TCLState>();

		for (TCLState state : states.getStates())
		{
			Map<TCLAction, Map<TCLState,Double>> lActionTransitions = new HashMap<TCLAction, Map<TCLState,Double>>();

			for (TCLAction action : actions.getActions())
			{
				nextStates.clear();

				nextLow  = computeTemperatureTransition(agent, state.getMinTemp(), action.isOn());
				nextHigh = computeTemperatureTransition(agent, state.getMaxTemp(), action.isOn());

				if (state.getID() == 0 || state.getID() == maxID)
				{
					if (state.getID() == 0     && !action.isOn()) nextState = states.getState(0);
					if (state.getID() == 0     &&  action.isOn()) nextState = states.getState(1);
					if (state.getID() == maxID && !action.isOn()) nextState = states.getState(maxID-1);
					if (state.getID() == maxID &&  action.isOn()) nextState = states.getState(maxID);

					nextStates.add(nextState);
				}
				else
				{
					nextLow  = computeTemperatureTransition(agent, state.getMinTemp(), action.isOn());
					nextHigh = computeTemperatureTransition(agent, state.getMaxTemp(), action.isOn());

					nextState = states.findTemperatureState(nextLow);
					nextStates.add(nextState);
	
					while (nextState.getMaxTemp() < nextHigh)
					{
						nextState = states.getState(nextState.getID() + 1);
						nextStates.add(nextState);
					}
				}

				lActionTransitions.put(action, computeProbabilities(nextStates, nextLow, nextHigh));
			}

			fTransitionFunction.put(state, lActionTransitions);
		}
	}

	private double computeTemperatureTransition(TCLAgent agent, double inTemp, boolean on)
	{
		return TemperatureFunction.nextTemperature(inTemp, 
												   outTemp,
												   transitionDelta,
												   agent.getTCL().getBlueprint(),
												   new ControlAction(on ? 1 : 0));
	}

	private Map<TCLState, Double> computeProbabilities(Set<TCLState> pFutureStates, double pMinT, double pMaxT)
	{
		Map<TCLState, Long>   lTransitionLikelihoods   = new HashMap<TCLState, Long>();
		Map<TCLState, Double> lTransitionProbabilities = new HashMap<TCLState, Double>();

		if (pFutureStates.size() == 1)
		{
			for (TCLState state : pFutureStates)
				lTransitionProbabilities.put(state, 1d);
		}
		else
		{
			long   lSum   = 0;
			double lRange = pMaxT - pMinT;
			for (TCLState lState : pFutureStates)
			{
				double lPartMin = Math.max(pMinT, lState.getMinTemp());
				double lPartMax = Math.min(pMaxT, lState.getMaxTemp());
	
				double lCover = lPartMax - lPartMin;
				if (Double.isInfinite(lCover))
					lCover = lRange;
	
				long lLikelihood = Math.round(lCover / lRange * PRECISION);
	
				lSum = lSum + lLikelihood;
	
				if (lLikelihood > 0)
					lTransitionLikelihoods.put(lState, lLikelihood);
			}
	
			for (TCLState lState : lTransitionLikelihoods.keySet())
			{
				double lProbability = (double) lTransitionLikelihoods.get(lState) / PRECISION;
	
				lTransitionProbabilities.put(lState, lProbability);
			}
	
			if (PRECISION != lSum)
			{
				ConsoleOutput.println(pMinT + "\t" + pMaxT + "\t" + pFutureStates);
				System.err.println("TransitionFunction.computeProbabilities(): WARNING Leaking probability, value of loss " + (PRECISION - lSum));
			}
		}

		return lTransitionProbabilities;
	}

	public Map<TCLState, Double> getTransitionFunction(State s_i, Action a_i)
	{
		return fTransitionFunction.get(s_i).get(a_i);
	}
}
