/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author peter
 */
public class LiveVariablesAnalyzer {
  private final DfaValueFactory myFactory;
  private final Instruction[] myInstructions;
  private final MultiMap<Instruction, Instruction> myBackwardMap;

  public LiveVariablesAnalyzer(ControlFlow flow, DfaValueFactory factory) {
    myFactory = factory;
    myInstructions = flow.getInstructions();
    myBackwardMap = calcBackwardMap();
  }

  private List<Instruction> getSuccessors(Instruction i) {
    if (i instanceof GotoInstruction) {
      return Arrays.asList(myInstructions[((GotoInstruction)i).getOffset()]);
    }

    int index = i.getIndex();
    if (i instanceof ConditionalGotoInstruction) {
      return Arrays.asList(myInstructions[((ConditionalGotoInstruction)i).getOffset()], myInstructions[index + 1]);
    }

    if (i instanceof ReturnInstruction) {
      return Collections.emptyList();
    }

    return Arrays.asList(myInstructions[index + 1]);

  }

  private MultiMap<Instruction, Instruction> calcBackwardMap() {
    MultiMap<Instruction, Instruction> result = MultiMap.create();
    for (Instruction instruction : myInstructions) {
      for (Instruction next : getSuccessors(instruction)) {
        result.putValue(next, instruction);
      }
    }
    return result;
  }

  private Map<FinishElementInstruction, BitSet> findLiveVars() {
    final Map<FinishElementInstruction, BitSet> result = ContainerUtil.newHashMap();

    runDfa(false, new PairFunction<Instruction, BitSet, BitSet>() {
      @Override
      public BitSet fun(Instruction instruction, BitSet liveVars) {
        if (instruction instanceof FinishElementInstruction) {
          BitSet set = result.get(instruction);
          if (set != null) {
            set.or(liveVars);
            return set;
          } else {
            result.put((FinishElementInstruction)instruction, liveVars);
          }
        }

        if (instruction instanceof PushInstruction) {
          DfaValue value = ((PushInstruction)instruction).getValue();
          if (value instanceof DfaVariableValue) {
            if (((PushInstruction)instruction).isReferenceWrite()) {
              liveVars = (BitSet)liveVars.clone();
              liveVars.clear(value.getID());
              for (DfaVariableValue var : myFactory.getVarFactory().getAllQualifiedBy((DfaVariableValue)value)) {
                liveVars.clear(var.getID());
              }
            } else if (!liveVars.get(value.getID())) {
              liveVars = (BitSet)liveVars.clone();
              liveVars.set(value.getID());
            }
          }
        } else if (instruction instanceof FlushVariableInstruction) {
          DfaVariableValue variable = ((FlushVariableInstruction)instruction).getVariable();
          if (variable != null) {
            liveVars = (BitSet)liveVars.clone();
            liveVars.clear(variable.getID());
            for (DfaVariableValue var : myFactory.getVarFactory().getAllQualifiedBy(variable)) {
              liveVars.clear(var.getID());
            }
          }
        }

        return liveVars;
      }
    });
    return result;
  }

  void flushDeadVariablesOnStatementFinish() {
    final Map<FinishElementInstruction, BitSet> liveVars = findLiveVars();

    runDfa(true, new PairFunction<Instruction, BitSet, BitSet>() {
      @Override
      @NotNull
      public BitSet fun(Instruction instruction, @NotNull BitSet prevLiveVars) {
        if (instruction instanceof FinishElementInstruction) {
          BitSet currentlyLive = liveVars.get(instruction);
          if (currentlyLive == null) {
            return new BitSet(); // an instruction unreachable from the end?
          }
          int index = 0;
          while (true) {
            int setBit = prevLiveVars.nextSetBit(index);
            if (setBit < 0) break;
            if (!currentlyLive.get(setBit)) {
              ((FinishElementInstruction)instruction).getVarsToFlush().add((DfaVariableValue)myFactory.getValue(setBit));
            }
            index = setBit + 1;
          }
          return currentlyLive;
        }

        return prevLiveVars;
      }
    });
  }

  private void runDfa(boolean forward, PairFunction<Instruction, BitSet, BitSet> handleState) {
    Set<Instruction> entryPoints = ContainerUtil.newHashSet();
    if (forward) {
      entryPoints.add(myInstructions[0]);
    } else {
      entryPoints.addAll(ContainerUtil.findAll(myInstructions, FilteringIterator.instanceOf(ReturnInstruction.class)));
    }

    Queue<InstructionState> queue = new Queue<InstructionState>(10);
    for (Instruction i : entryPoints) {
      queue.addLast(new InstructionState(i, new BitSet()));
    }

    int steps = 0;
    Set<InstructionState> processed = ContainerUtil.newHashSet();
    while (!queue.isEmpty()) {
      steps++;
      InstructionState state = queue.pullFirst();
      Instruction instruction = state.first;
      Collection<Instruction> nextInstructions = forward ? getSuccessors(instruction) : myBackwardMap.get(instruction);
      boolean branching = nextInstructions.size() > 1 || !forward && instruction.getIndex() == 0;
      BitSet nextVars = handleState.fun(instruction, state.second);
      for (Instruction next : nextInstructions) {
        InstructionState nextState = new InstructionState(next, nextVars);
        if (!branching || processed.add(nextState)) {
          queue.addLast(nextState);
        }
      }
    }
    if (steps > 10000) {
      int a = 1;
    }
  }

  private static class InstructionState extends Pair<Instruction, BitSet> {
    public InstructionState(Instruction first, BitSet second) {
      super(first, second);
    }
  }
}
