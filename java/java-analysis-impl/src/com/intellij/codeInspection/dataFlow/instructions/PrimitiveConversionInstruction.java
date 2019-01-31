// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.Nullable;

public class PrimitiveConversionInstruction extends Instruction implements ExpressionPushingInstruction {
  @Nullable private final PsiPrimitiveType myTargetType;
  @Nullable private final PsiExpression myExpression;

  public PrimitiveConversionInstruction(@Nullable PsiPrimitiveType targetType, @Nullable PsiExpression expression) {
    myTargetType = targetType;
    myExpression = expression;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitConvertPrimitive(this, runner, stateBefore);
  }

  @Nullable
  public PsiPrimitiveType getTargetType() {
    return myTargetType;
  }

  @Override
  @Nullable
  public PsiExpression getExpression() {
    return myExpression;
  }

  @Override
  public String toString() {
    return "CONVERT_PRIMITIVE";
  }
}
