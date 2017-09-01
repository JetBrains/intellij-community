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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.*;
import com.intellij.util.containers.Queue;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
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
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") private final Map<PsiElement, List<DfaVariableValue>> myClosureReads = FactoryMap.createMap(closure-> {
      final Set<DfaVariableValue> result = ContainerUtil.newLinkedHashSet();
      closure.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (element instanceof PsiReferenceExpression) {
            DfaValue value = myFactory.createValue((PsiReferenceExpression)element);
            if (value instanceof DfaVariableValue) {
              result.add((DfaVariableValue)value);
            }
          }
          super.visitElement(element);
        }
      });
      return ContainerUtil.newArrayList(result);
    }
  );

  public LiveVariablesAnalyzer(ControlFlow flow, DfaValueFactory factory) {
    myFactory = factory;
    myInstructions = flow.getInstructions();
    myForwardMap = calcForwardMap();
    myBackwardMap = calcBackwardMap();
  }

  private List<Instruction> getSuccessors(Instruction ins) {
    return IntStreamEx.of(LoopAnalyzer.getSuccessorIndices(ins.getIndex(), myInstructions)).mapToObj(i -> myInstructions[i]).toList();
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

  @Nullable
  private static DfaVariableValue getWrittenVariable(Instruction instruction) {
    if (instruction instanceof AssignInstruction) {
      DfaValue value = ((AssignInstruction)instruction).getAssignedValue();
      return value instanceof DfaVariableValue ? (DfaVariableValue)value : null;
    }
    if (instruction instanceof FlushVariableInstruction) return ((FlushVariableInstruction)instruction).getVariable();
    return null;
  }

  @NotNull
  private List<DfaVariableValue> getReadVariables(Instruction instruction) {
    if (instruction instanceof PushInstruction && !((PushInstruction)instruction).isReferenceWrite()) {
      DfaValue value = ((PushInstruction)instruction).getValue();
      if (value instanceof DfaVariableValue) {
        return Collections.singletonList((DfaVariableValue)value);
      }
    } else {
      PsiElement closure = DfaUtil.getClosureInside(instruction);
      if (closure != null) {
        return myClosureReads.get(closure);
      }
    }
    return Collections.emptyList();
  }

  private boolean isInterestingInstruction(Instruction instruction) {
    if (instruction == myInstructions[0]) return true;
    if (!getReadVariables(instruction).isEmpty() || getWrittenVariable(instruction) != null) return true;
    return instruction instanceof FinishElementInstruction ||
           instruction instanceof GotoInstruction ||
           instruction instanceof ConditionalGotoInstruction ||
           instruction instanceof ControlTransferInstruction;
  }

  @Nullable
  private Map<FinishElementInstruction, BitSet> findLiveVars() {
    final Map<FinishElementInstruction, BitSet> result = ContainerUtil.newHashMap();

    boolean ok = runDfa(false, (instruction, liveVars) -> {
      if (instruction instanceof FinishElementInstruction) {
        BitSet set = result.get(instruction);
        if (set != null) {
          set.or(liveVars);
          return (BitSet)set.clone();
        }
        else if (!liveVars.isEmpty()) {
          result.put((FinishElementInstruction)instruction, (BitSet)liveVars.clone());
        }
      }

      DfaVariableValue written = getWrittenVariable(instruction);
      if (written != null) {
        liveVars = (BitSet)liveVars.clone();
        liveVars.clear(written.getID());
        for (DfaVariableValue var : myFactory.getVarFactory().getAllQualifiedBy(written)) {
          liveVars.clear(var.getID());
        }
      } else {
        boolean cloned = false;
        for (DfaVariableValue value : getReadVariables(instruction)) {
          if (!liveVars.get(value.getID())) {
            if (!cloned) {
              liveVars = (BitSet)liveVars.clone();
              cloned = true;
            }
            liveVars.set(value.getID());
          }
        }
      }

      return liveVars;
    });
    return ok ? result : null;
  }

  void flushDeadVariablesOnStatementFinish() {
    final Map<FinishElementInstruction, BitSet> liveVars = findLiveVars();
    if (liveVars == null) return;

    final MultiMap<FinishElementInstruction, DfaVariableValue> toFlush = MultiMap.createSet();

    boolean ok = runDfa(true, (instruction, prevLiveVars) -> {
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
    });

    if (ok) {
      for (FinishElementInstruction instruction : toFlush.keySet()) {
        Collection<DfaVariableValue> values = toFlush.get(instruction);
        // Do not flush special values as they could be used implicitly
        values.removeIf(var -> var.getQualifier() != null &&
                               StreamEx.of(SpecialField.values()).anyMatch(sf -> sf.isMyAccessor(var.getPsiVariable())));
        instruction.getVarsToFlush().addAll(values);
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

    Queue<InstructionState> queue = new Queue<>(10);
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
