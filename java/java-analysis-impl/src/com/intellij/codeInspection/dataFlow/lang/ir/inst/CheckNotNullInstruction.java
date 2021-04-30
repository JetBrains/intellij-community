/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.jvm.ControlTransferHandler;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.NOT_NULL_OBJECT;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.NULL;

public class CheckNotNullInstruction extends Instruction {
  private final @NotNull NullabilityProblemKind.NullabilityProblem<?> myProblem;
  private final @Nullable DfaControlTransferValue myTransferValue;

  public CheckNotNullInstruction(@NotNull NullabilityProblemKind.NullabilityProblem<?> problem,
                                 @Nullable DfaControlTransferValue transferValue) {
    myProblem = problem;
    myTransferValue = transferValue;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myTransferValue == null) return this;
    var instruction = new CheckNotNullInstruction(myProblem, myTransferValue.bindToFactory(factory));
    instruction.setIndex(getIndex());
    return instruction;
  }

  @NotNull
  public NullabilityProblemKind.NullabilityProblem<?> getProblem() {
    return myProblem;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore) {
    NullabilityProblemKind.NullabilityProblem<?> problem = getProblem();
    if (problem.thrownException() == null) {
      checkNotNullable(runner, stateBefore, stateBefore.peek(), problem);
    } else {
      DfaValue value = stateBefore.pop();
      boolean isNull = runner instanceof StandardDataFlowRunner && ((StandardDataFlowRunner)runner).stopOnNull() && stateBefore.isNull(value);
      if (myTransferValue == null) {
        stateBefore.push(dereference(runner, stateBefore, value, problem));
        if (isNull) {
          return DfaInstructionState.EMPTY_ARRAY;
        }
      } else {
        List<DfaInstructionState> result = new ArrayList<>();
        DfaMemoryState nullState = stateBefore.createCopy();
        stateBefore.push(dereference(runner, stateBefore, value, problem));
        if (!isNull) {
          result.add(nextState(runner, stateBefore));
        }
        if (nullState.applyCondition(value.eq(NULL))) {
          List<DfaInstructionState> dispatched = ControlTransferHandler.dispatch(nullState, runner, myTransferValue);
          for (DfaInstructionState npeState : dispatched) {
            npeState.getMemoryState().markEphemeral();
          }
          result.addAll(dispatched);
        }
        return result.toArray(DfaInstructionState.EMPTY_ARRAY);
      }
    }
    return nextStates(runner, stateBefore);
  }

  @Override
  public String toString() {
    return "CHECK_NOT_NULL " + myProblem;
  }

  static <T extends PsiElement> DfaValue dereference(@NotNull DataFlowRunner runner, 
                                                     @NotNull DfaMemoryState memState,
                                                     @NotNull DfaValue value,
                                                     @Nullable NullabilityProblemKind.NullabilityProblem<T> problem) {
    checkNotNullable(runner, memState, value, problem);
    if (value instanceof DfaTypeValue) {
      DfType dfType = value.getDfType().meet(NOT_NULL_OBJECT);
      return value.getFactory().fromDfType(dfType == DfType.BOTTOM ? NOT_NULL_OBJECT : dfType);
    }
    if (memState.isNull(value) && problem != null && problem.getKind() == NullabilityProblemKind.nullableFunctionReturn) {
      return value.getFactory().fromDfType(NOT_NULL_OBJECT);
    }
    if (value instanceof DfaVariableValue) {
      DfType dfType = memState.getDfType(value);
      if (dfType == NULL) {
        memState.setDfType(value, NOT_NULL_OBJECT);
      }
      else {
        memState.meetDfType(value, NOT_NULL_OBJECT);
      }
    }
    return value;
  }

  static void checkNotNullable(@NotNull DataFlowRunner runner, 
                               @NotNull DfaMemoryState state,
                               @NotNull DfaValue value,
                               @Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
    if (problem != null) {
      DfaNullability nullability = DfaNullability.fromDfType(state.getDfType(value));
      ThreeState failed = nullability == DfaNullability.NOT_NULL ? ThreeState.NO :
                          nullability == DfaNullability.NULL ? ThreeState.YES : ThreeState.UNSURE;
      runner.getInterceptor().onCondition(problem, value, failed, state);
    }
  }
}
