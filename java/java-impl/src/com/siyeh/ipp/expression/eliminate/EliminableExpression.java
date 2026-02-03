// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression.eliminate;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPolyadicExpression;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class EliminableExpression {

  protected final PsiPolyadicExpression myExpression;
  protected final PsiExpression myOperand;

  @Contract(pure = true)
  protected EliminableExpression(@Nullable PsiPolyadicExpression expression, @NotNull PsiExpression operand) {
    myExpression = expression;
    myOperand = operand;
  }

  /**
   * Eliminate {@link #myOperand} in a context of {@link #myExpression} (possibly null) and write result into sb.
   *
   * @param tokenBefore token before {@link #myExpression} (if it's not null) or operand
   * @param sb          string builder to append result to
   * @return true if eliminated, false otherwise
   */
  abstract boolean eliminate(@Nullable PsiJavaToken tokenBefore, @NotNull StringBuilder sb);

  @NotNull
  PsiExpression getExpressionToReplace() {
    return myExpression != null ? myExpression : myOperand;
  }

  PsiPolyadicExpression getExpression() {
    return myExpression;
  }

  PsiExpression getOperand() {
    return myOperand;
  }
}
