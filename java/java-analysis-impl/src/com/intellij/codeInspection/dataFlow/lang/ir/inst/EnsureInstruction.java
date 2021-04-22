// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Instruction to ensure that a specific condition is hold on top-of-stack value
 */
public class EnsureInstruction extends Instruction {
  private final @Nullable PsiElement myExpression;
  private final @NotNull RelationType myRelation;
  private final @NotNull DfType myCompareTo;
  private final @Nullable DfaControlTransferValue myTransferValue;

  public EnsureInstruction(@Nullable PsiElement expression,
                           @NotNull RelationType relation,
                           @NotNull DfType compareTo,
                           @Nullable DfaControlTransferValue value) {
    myExpression = expression;
    myRelation = relation;
    myCompareTo = compareTo;
    myTransferValue = value;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myTransferValue == null) return this;
    var instruction = new EnsureInstruction(myExpression, myRelation, myCompareTo,
                                            myTransferValue.bindToFactory(factory));
    instruction.setIndex(getIndex());
    return instruction;
  }

  public @Nullable PsiElement getPsiAnchor() {
    return myExpression;
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
