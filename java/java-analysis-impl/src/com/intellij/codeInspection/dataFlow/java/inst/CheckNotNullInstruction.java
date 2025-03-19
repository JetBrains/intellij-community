// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
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
    return new CheckNotNullInstruction(myProblem, myTransferValue.bindToFactory(factory));
  }

  public @NotNull NullabilityProblemKind.NullabilityProblem<?> getProblem() {
    return myProblem;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    NullabilityProblemKind.NullabilityProblem<?> problem = getProblem();
    if (problem.thrownException() == null) {
      checkNotNullable(interpreter, stateBefore, stateBefore.peek(), problem);
    } else {
      DfaValue value = stateBefore.pop();
      boolean isNull = interpreter instanceof StandardDataFlowInterpreter standard &&
                       standard.stopOnNull() &&
                       stateBefore.getDfType(value) == NULL;
      if (myTransferValue == null) {
        stateBefore.push(dereference(interpreter, stateBefore, value, problem));
        if (isNull) {
          return DfaInstructionState.EMPTY_ARRAY;
        }
      } else {
        List<DfaInstructionState> result = new ArrayList<>();
        DfaMemoryState nullState = stateBefore.createCopy();
        stateBefore.push(dereference(interpreter, stateBefore, value, problem));
        if (!isNull) {
          result.add(nextState(interpreter, stateBefore));
        }
        if (nullState.applyCondition(value.eq(NULL))) {
          List<DfaInstructionState> dispatched = myTransferValue.dispatch(nullState, interpreter);
          for (DfaInstructionState npeState : dispatched) {
            npeState.getMemoryState().markEphemeral();
          }
          result.addAll(dispatched);
        }
        return result.toArray(DfaInstructionState.EMPTY_ARRAY);
      }
    }
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public String toString() {
    return "CHECK_NOT_NULL " + myProblem;
  }

  static <T extends PsiElement> DfaValue dereference(@NotNull DataFlowInterpreter interpreter,
                                                     @NotNull DfaMemoryState memState,
                                                     @NotNull DfaValue value,
                                                     @Nullable NullabilityProblemKind.NullabilityProblem<T> problem) {
    checkNotNullable(interpreter, memState, value, problem);
    if (value instanceof DfaTypeValue) {
      DfType dfType = value.getDfType().meet(NOT_NULL_OBJECT);
      return value.getFactory().fromDfType(dfType == DfType.BOTTOM ? NOT_NULL_OBJECT : dfType);
    }
    DfType dfType = memState.getDfType(value);
    if (dfType == NULL) {
      if (problem != null && problem.getKind() == NullabilityProblemKind.nullableFunctionReturn) {
        return value.getFactory().fromDfType(NOT_NULL_OBJECT);
      }
      memState.setDfType(value, NOT_NULL_OBJECT);
    }
    else if (value instanceof DfaVariableValue) {
      memState.meetDfType(value, NOT_NULL_OBJECT);
    }
    return value;
  }

  static void checkNotNullable(@NotNull DataFlowInterpreter interpreter,
                               @NotNull DfaMemoryState state,
                               @NotNull DfaValue value,
                               @Nullable NullabilityProblemKind.NullabilityProblem<?> problem) {
    if (problem != null) {
      DfaNullability nullability = DfaNullability.fromDfType(state.getDfType(value));
      ThreeState failed = nullability == DfaNullability.NOT_NULL ? ThreeState.NO :
                          nullability == DfaNullability.NULL ? ThreeState.YES : ThreeState.UNSURE;
      interpreter.getListener().onCondition(problem, value, failed, state);
    }
  }
}
