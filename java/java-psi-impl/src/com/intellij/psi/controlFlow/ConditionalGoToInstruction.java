// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public class ConditionalGoToInstruction extends ConditionalBranchingInstruction {
  ConditionalGoToInstruction(int offset, final PsiExpression expression) {
    this(offset, Role.END, expression);
  }
  ConditionalGoToInstruction(int offset, @NotNull Role role, final PsiExpression expression) {
    super(offset, expression, role);
  }

  public String toString() {
    @NonNls final String sRole = "[" + role + "]";
    return "COND_GOTO " + sRole + " " + offset;
  }

  @Override
  public void accept(@NotNull ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitConditionalGoToInstruction(this, offset, nextOffset);
  }
}
