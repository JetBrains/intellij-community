// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.PsiMethodCallExpression;

public class IsAssignableInstruction extends ExpressionPushingInstruction<PsiMethodCallExpression> {
  public IsAssignableInstruction(PsiMethodCallExpression expression) {
    super(expression);
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitIsAssignableFromInstruction(this, runner, stateBefore);
  }

  @Override
  public String toString() {
    return "IS_ASSIGNABLE_FROM";
  }
}
