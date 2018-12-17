// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Instruction to push a field qualified by the value on the stack
 */
public class GetFieldInstruction extends Instruction implements ExpressionPushingInstruction {
  @Nullable private final PsiType myTargetType;
  @NotNull private final VariableDescriptor myDescriptor;
  @Nullable private final PsiExpression myAnchor;

  public GetFieldInstruction(@NotNull VariableDescriptor descriptor, @Nullable PsiType targetType) {
    this(null, descriptor, targetType);
  }

  public GetFieldInstruction(@Nullable PsiExpression anchor, @NotNull VariableDescriptor descriptor, @Nullable PsiType targetType) {
    myTargetType = targetType;
    myDescriptor = descriptor;
    myAnchor = anchor;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitGetField(this, runner, stateBefore);
  }

  @NotNull
  public VariableDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Nullable
  public PsiType getTargetType() {
    return myTargetType;
  }

  @Override
  public String toString() {
    return "GET_FIELD " + myDescriptor;
  }

  @Nullable
  @Override
  public PsiExpression getExpression() {
    return myAnchor;
  }
}
