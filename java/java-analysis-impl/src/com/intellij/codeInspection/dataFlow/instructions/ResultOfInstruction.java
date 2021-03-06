// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Instruction that does nothing but allows to attach an expression to the top-of-stack
 */
public class ResultOfInstruction extends EvalInstruction {
  public ResultOfInstruction(@NotNull PsiExpression expression) {
    super(expression, 1);
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    return arguments[0];
  }

  public String toString() {
    return "RESULT_OF "+getExpression().getText();
  }

  @NotNull
  @Override
  public PsiExpression getExpression() {
    return Objects.requireNonNull(super.getExpression());
  }
}
