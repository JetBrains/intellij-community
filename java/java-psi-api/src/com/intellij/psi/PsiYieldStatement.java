// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code yield} statement.
 */
public interface PsiYieldStatement extends PsiStatement {
  /**
   * Returns the value expression of the statement, if present.
   */
  @Nullable PsiExpression getExpression();

  /**
   * Returns the {@link PsiSwitchExpression switch expression}, out of which the statement transfers control.
   */
  @Nullable PsiSwitchExpression findEnclosingExpression();
}