// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code assert} statement.
 */
public interface PsiAssertStatement extends PsiStatement{
  /**
   * Returns the expression representing the asserted condition.
   *
   * @return the asserted condition expression, or {@code null} if the assert statement
   * is incomplete.
   */
  @Nullable
  PsiExpression getAssertCondition();

  /**
   * Returns the expression representing the description of the assert.
   *
   * @return the assert description expression, or {@code null} if none has been specified.
   */
  @Nullable
  PsiExpression getAssertDescription();
}