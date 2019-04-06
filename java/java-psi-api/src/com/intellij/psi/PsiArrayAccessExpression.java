// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an array access expression, for example, {@code a[1]}.
 */
public interface PsiArrayAccessExpression extends PsiExpression {
  /**
   * Returns the expression specifying the accessed array.
   *
   * @return the array expression.
   */
  @NotNull
  PsiExpression getArrayExpression();

  /**
   * Returns the index expression (the expression in square brackets).
   *
   * @return the index expression, or {@code null} if the array access expression is incomplete.
   */
  @Nullable
  PsiExpression getIndexExpression();
}
