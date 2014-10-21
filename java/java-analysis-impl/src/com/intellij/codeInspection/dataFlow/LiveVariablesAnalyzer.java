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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class LiveVariablesAnalyzer {
  private final DfaValueFactory myFactory;
  private final Instruction[] myInstructions;
  private final MultiMap<Instruction, Instruction> myForwardMap;
  private final MultiMap<Instruction, Instruction> myBackwardMap;

  public LiveVariablesAnalyzer(ControlFlow flow, DfaValueFactory factory) {
    myFactory = factory;
    myInstructions = flow.getInstructions();
    myForwardMap = calcForwardMap();
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
      for (Instruction next : myForwardMap.get(instruction)) {
        result.putValue(next, instruction);
      }
    }
    return result;
  }

  private MultiMap<Instruction, Instruction> calcForwardMap() {
    MultiMap<Instruction, Instruction> result = MultiMap.create();
    for (Instruction instruction : myInstructions) {
      if (isInterestingInstruction(instruction)) {
        for (Instruction next : getSuccessors(instruction)) {
          while (true) {
            if (isInterestingInstruction(next)) {
              result.putValue(instruction, next);
              break;
            }
            if (next.getIndex() + 1 >= myInstructions.length) {
              break;
            }
            next = myInstructions[next.getIndex() + 1];
          }
        }
      }
    }
    return result;
  }

  private boolean isInterestingInstruction(Instruction instruction) {
    if (instruction == myInstructions[0]) return true;
    if (instruction instanceof PushInstruction) return ((PushInstruction)instruction).getValue() instanceof DfaVariableValue;
    if (instruction instanceof AssignInstruction) return ((AssignInstruction)instruction).getAssignedValue() != null;
    return instruction instanceof FinishElementInstruction ||
           instruction instanceof FlushVariableInstruction ||
           instruction instanceof GotoInstruction ||
           instruction instanceof ConditionalGotoInstruction ||
           instruction instanceof ReturnInstruction;
  }

  @Nullable
  private Map<FinishElementInstruction, BitSet> findLiveVars() {
    final Map<FinishElementInstruction, BitSet> result = ContainerUtil.newHashMap();

    boolean ok = runDfa(false, new PairFunction<Instruction, BitSet, BitSet>() {
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

        if (instruction instanceof AssignInstruction) {
          DfaValue value = ((AssignInstruction)instruction).getAssignedValue();
          if (value instanceof DfaVariableValue) {
            liveVars = (BitSet)liveVars.clone();
            liveVars.clear(value.getID());
            for (DfaVariableValue var : myFactory.getVarFactory().getAllQualifiedBy((DfaVariableValue)value)) {
              liveVars.clear(var.getID());
            }
          }
        }

        if (instruction instanceof PushInstruction) {
          DfaValue value = ((PushInstruction)instruction).getValue();
          if (value instanceof DfaVariableValue) {
            if (!((PushInstruction)instruction).isReferenceWrite() && !liveVars.get(value.getID())) {
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
    return ok ? result : null;
  }

  void flushDeadVariablesOnStatementFinish() {
    final Map<FinishElementInstruction, BitSet> liveVars = findLiveVars();
    if (liveVars == null) return;

    final MultiMap<FinishElementInstruction, DfaVariableValue> toFlush = MultiMap.createSet();

    boolean ok = runDfa(true, new PairFunction<Instruction, BitSet, BitSet>() {
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
              toFlush.putValue((FinishElementInstruction)instruction, (DfaVariableValue)myFactory.getValue(setBit));
            }
            index = setBit + 1;
          }
          return currentlyLive;
        }

        return prevLiveVars;
      }
    });

    if (ok) {
      for (FinishElementInstruction instruction : toFlush.keySet()) {
        instruction.getVarsToFlush().addAll(toFlush.get(instruction));
      }
    }
  }

  /**
   * @return true if completed, false if "too complex"
   */
  private boolean runDfa(boolean forward, PairFunction<Instruction, BitSet, BitSet> handleState) {
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

    int limit = myForwardMap.size() * 20;
    Set<InstructionState> processed = ContainerUtil.newHashSet();
    while (!queue.isEmpty()) {
      int steps = processed.size();
      if (steps > limit) {
        return false;
      }
      if (steps % 1024 == 0) {
        ProgressManager.checkCanceled();
      }
      InstructionState state = queue.pullFirst();
      Instruction instruction = state.first;
      Collection<Instruction> nextInstructions = forward ? myForwardMap.get(instruction) : myBackwardMap.get(instruction);
      BitSet nextVars = handleState.fun(instruction, state.second);
      for (Instruction next : nextInstructions) {
        InstructionState nextState = new InstructionState(next, nextVars);
        if (processed.add(nextState)) {
          queue.addLast(nextState);
        }
      }
    }
    return true;
  }

  private static class InstructionState extends Pair<Instruction, BitSet> {
    public InstructionState(Instruction first, BitSet second) {
      super(first, second);
    }
  }
}
