// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    var instruction = new EnsureInstruction(myProblem, myRelation, myCompareTo, myTransferValue.bindToFactory(factory));
    instruction.setIndex(getIndex());
    return instruction;
  }

  public boolean isMakeEphemeral() {
    return myMakeEphemeral;
  }

  public @Nullable UnsatisfiedConditionProblem getProblem() {
    return myProblem;
  }

  /**
   * @return transfer value to apply if condition doesn't hold
   */
  @Nullable
  public DfaControlTransferValue getExceptionTransfer() {
    return myTransferValue;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner,
                                      DfaMemoryState stateBefore,
                                      InstructionVisitor visitor) {
    return visitor.visitEnsure(this, runner, stateBefore);
  }

  public @NotNull DfaCondition createCondition(@NotNull DfaValue tosValue) {
    return tosValue.cond(myRelation, myCompareTo);
  }

  @Override
  public String toString() {
    return "ENSURE " + myRelation + " " + myCompareTo + (myTransferValue == null ? "" : " " + myTransferValue.getTarget());
  }
}
