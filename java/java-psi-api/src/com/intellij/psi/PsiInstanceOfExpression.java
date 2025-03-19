// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a Java {@code instanceof} expression.
 */
public interface PsiInstanceOfExpression extends PsiExpression {
  /**
   * Returns the expression on the left side of the {@code instanceof}.
   *
   * @return the checked expression.
   */
  @NotNull
  PsiExpression getOperand();

  /**
   * Returns the type element on the right side of the {@code instanceof}.
   *
   * @return the type element, or null if the expression is incomplete or matches against a pattern.
   */
  @Nullable
  PsiTypeElement getCheckType();

  /**
   * @return pattern against which operand will be matched
   */
  @Nullable
  PsiPrimaryPattern getPattern();
}
