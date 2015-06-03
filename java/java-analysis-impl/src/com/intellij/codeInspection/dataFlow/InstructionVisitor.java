/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class InstructionVisitor {

  protected final AbstractDataFlowRunner myRunner;

  public InstructionVisitor(AbstractDataFlowRunner runner) {
    myRunner = runner;
  }

  @NotNull
  public static DfaInstructionState[] nextInstruction(@NotNull Instruction instruction,
                                                      @NotNull AbstractDataFlowRunner runner,
                                                      @NotNull DfaMemoryState stateBefore) {
    return nextInstruction(instruction, runner.getInstructions(), stateBefore);
  }

  @NotNull
  public static DfaInstructionState[] nextInstruction(@NotNull Instruction instruction,
                                                      @NotNull Instruction[] flow,
                                                      @NotNull DfaMemoryState stateBefore) {
    return new DfaInstructionState[]{new DfaInstructionState(flow[instruction.getIndex() + 1], stateBefore)};
  }

  public DfaInstructionState[] nextInstruction(@NotNull Instruction instruction, DfaMemoryState state) {
    return nextInstruction(instruction, myRunner, state);
  }

  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DfaMemoryState memState) {
    memState.pop();
    memState.pop();
    memState.push(DfaUnknownValue.getInstance());
    return nextInstruction(instruction, memState);
  }

  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction, DfaMemoryState memState) {
    memState.pop();
    return nextInstruction(instruction, memState);
  }

  public DfaInstructionState[] visitConditionalGoto(ConditionalGotoInstruction instruction, DfaMemoryState memState) {
    DfaValue cond = memState.pop();

    DfaValue condTrue;
    DfaValue condFalse;

    if (instruction.isNegated()) {
      condFalse = cond;
      condTrue = cond.createNegated();
    }
    else {
      condTrue = cond;
      condFalse = cond.createNegated();
    }

    if (condTrue == myRunner.getFactory().getConstFactory().getTrue()) {
      markBranchReachable(instruction, true);
      return new DfaInstructionState[]{new DfaInstructionState(myRunner.getInstruction(instruction.getOffset()), memState)};
    }

    if (condFalse == myRunner.getFactory().getConstFactory().getTrue()) {
      markBranchReachable(instruction, false);
      return nextInstruction(instruction, memState);
    }

    ArrayList<DfaInstructionState> result = new ArrayList<DfaInstructionState>();

    DfaMemoryState thenState = memState.createCopy();
    DfaMemoryState elseState = memState.createCopy();

    if (thenState.applyCondition(condTrue)) {
      result.add(new DfaInstructionState(myRunner.getInstruction(instruction.getOffset()), thenState));
      markBranchReachable(instruction, true);
    }

    if (elseState.applyCondition(condFalse)) {
      result.add(new DfaInstructionState(myRunner.getInstruction(instruction.getIndex() + 1), elseState));
      markBranchReachable(instruction, false);
    }

    return result.toArray(new DfaInstructionState[result.size()]);
  }

  private static void markBranchReachable(ConditionalGotoInstruction instruction, boolean isTrueBranch) {
    if (isTrueBranch ^ instruction.isNegated()) {
      instruction.setTrueReachable();
    }
    else {
      instruction.setFalseReachable();
    }
  }


  public DfaInstructionState[] visitEmptyStack(EmptyStackInstruction instruction, DfaMemoryState memState) {
    memState.emptyStack();
    return nextInstruction(instruction, memState);
  }

  public DfaInstructionState[] visitFlushVariable(FlushVariableInstruction instruction, DfaMemoryState memState) {
    final DfaVariableValue variable = instruction.getVariable();
    if (variable != null) {
      if (instruction.isDependentsOnly()) {
        for (DfaVariableValue qualified : myRunner.getFactory().getVarFactory().getAllQualifiedBy(variable)) {
          memState.flushVariable(qualified);
        }
      }
      else {
        memState.flushVariable(variable);
      }
    }
    else {
      memState.flushFields();
    }
    return nextInstruction(instruction, memState);
  }

  public DfaInstructionState[] visitNot(NotInstruction instruction, DfaMemoryState memState) {
    DfaValue dfaValue = memState.pop();

    dfaValue = dfaValue.createNegated();
    memState.push(dfaValue);
    return nextInstruction(instruction, memState);
  }

  public DfaInstructionState[] visitPush(PushInstruction instruction, DfaMemoryState memState) {
    memState.push(instruction.getValue());
    return nextInstruction(instruction, memState);
  }

  public DfaInstructionState[] visitEmptyInstruction(EmptyInstruction instruction, DfaMemoryState before) {
    return nextInstruction(instruction, before);
  }

  public DfaInstructionState[] visitGoto(GotoInstruction instruction, DfaMemoryState state) {
    Instruction nextInstruction = myRunner.getInstruction(instruction.getOffset());
    return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, state)};
  }

  public DfaInstructionState[] visitDuplicate(DupInstruction instruction, DfaMemoryState state) {
    final int duplicationCount = instruction.getDuplicationCount();
    final int valueCount = instruction.getValueCount();
    if (duplicationCount == 1 && valueCount == 1) {
      state.push(state.peek());
    }
    else {
      List<DfaValue> values = new ArrayList<DfaValue>(valueCount);
      for (int i = 0; i < valueCount; i++) {
        values.add(state.pop());
      }
      for (int j = 0; j < duplicationCount + 1; j++) {
        for (int i = values.size() - 1; i >= 0; i--) {
          state.push(values.get(i));
        }
      }
    }
    return nextInstruction(instruction, state);
  }

  public DfaInstructionState[] visitFinishElement(FinishElementInstruction instruction, DfaMemoryState state) {
    for (DfaVariableValue value : instruction.getVarsToFlush()) {
      state.flushVariable(value);
    }
    return nextInstruction(instruction, state);
  }

  public DfaInstructionState[] visitPop(PopInstruction instruction, DfaMemoryState state) {
    state.pop();
    return nextInstruction(instruction, state);
  }

  public DfaInstructionState[] visitSwap(SwapInstruction instruction, DfaMemoryState stateBefore) {
    final DfaValue a = stateBefore.pop();
    final DfaValue b = stateBefore.pop();
    stateBefore.push(a);
    stateBefore.push(b);
    return nextInstruction(instruction, stateBefore);
  }
}
