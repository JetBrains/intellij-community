// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThreeState;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Instruction to ensure that a specific condition is hold on top-of-stack value
 */
public class EnsureInstruction extends Instruction {
  private final @Nullable UnsatisfiedConditionProblem myProblem;
  private final @NotNull RelationType myRelation;
  private final @NotNull DfType myCompareTo;
  private final @Nullable DfaControlTransferValue myTransferValue;
  private final boolean myMakeEphemeral;

  /**
   * @param problem problem descriptor to report when condition is not satisfied
   * @param relation relation to apply to top-of-stack value
   * @param compareTo right operand of relation
   * @param value transfer to use if relation is not satisfied (can be null, in this case the interpretation simply finishes)
   */
  public EnsureInstruction(@Nullable UnsatisfiedConditionProblem problem,
                           @NotNull RelationType relation,
                           @NotNull DfType compareTo,
                           @Nullable DfaControlTransferValue value) {
    this(problem, relation, compareTo, value, false);
  }

  /**
   * @param problem problem descriptor to report when condition is not satisfied
   * @param relation relation to apply to top-of-stack value
   * @param compareTo right operand of relation
   * @param value transfer to use if relation is not satisfied (can be null, in this case the interpretation simply finishes)
   * @param makeEphemeral if true, memory states on unsatisfied condition will be marked as ephemeral
   */
  public EnsureInstruction(@Nullable UnsatisfiedConditionProblem problem,
                           @NotNull RelationType relation,
                           @NotNull DfType compareTo,
                           @Nullable DfaControlTransferValue value,
                           boolean makeEphemeral) {
    myProblem = problem;
    myRelation = relation;
    myCompareTo = compareTo;
    myTransferValue = value;
    myMakeEphemeral = makeEphemeral;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myTransferValue == null) return this;
    return new EnsureInstruction(myProblem, myRelation, myCompareTo, myTransferValue.bindToFactory(factory));
  }

  public @Nullable UnsatisfiedConditionProblem getProblem() {
    return myProblem;
  }

  /**
   * @return transfer value to apply if condition doesn't hold
   */
  public @Nullable DfaControlTransferValue getExceptionTransfer() {
    return myTransferValue;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue tosValue = stateBefore.isEmptyStack() ? interpreter.getFactory().getUnknown() : stateBefore.peek();
    DfaCondition cond = createCondition(tosValue);
    UnsatisfiedConditionProblem problem = getProblem();
    if (cond.equals(DfaCondition.getTrue())) {
      if (problem != null) {
        interpreter.getListener().onCondition(problem, tosValue, ThreeState.NO, stateBefore);
      }
      return nextStates(interpreter, stateBefore);
    }
    DfaMemoryState falseState = stateBefore.createCopy();
    boolean trueStatePossible = stateBefore.applyCondition(cond);
    boolean falseStatePossible = falseState.applyCondition(cond.negate());
    List<DfaInstructionState> result = new ArrayList<>();
    if (trueStatePossible) {
      result.add(nextState(interpreter, stateBefore));
    }
    if (problem != null) {
      ThreeState failed = !trueStatePossible ? ThreeState.YES :
                          !falseStatePossible ? ThreeState.NO : ThreeState.UNSURE;
      interpreter.getListener().onCondition(problem, tosValue, failed, stateBefore);
    }
    if (falseStatePossible && myTransferValue != null) {
      List<DfaInstructionState> states = myTransferValue.dispatch(falseState, interpreter);
      if (myMakeEphemeral) {
        for (DfaInstructionState negState : states) {
          negState.getMemoryState().markEphemeral();
        }
      }
      result.addAll(states);
    }
    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  @Override
  public boolean isLinear() {
    return myTransferValue == null;
  }

  @Override
  public int @NotNull [] getSuccessorIndexes() {
    if (myTransferValue == null) {
      return new int[]{getIndex() + 1};
    }
    return ArrayUtil.append(myTransferValue.getPossibleTargetIndices(), getIndex() + 1);
  }

  public @NotNull DfaCondition createCondition(@NotNull DfaValue tosValue) {
    return tosValue.cond(myRelation, myCompareTo);
  }

  @Override
  public String toString() {
    return "ENSURE " + myRelation + " " + myCompareTo + (myTransferValue == null ? "" : " " + myTransferValue.getTarget());
  }
}
