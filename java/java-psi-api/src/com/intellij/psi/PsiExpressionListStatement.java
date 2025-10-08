// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a list of expressions separated by commas, used as the initialize
 * or update part of a {@code for} loop.
 * 
 * @see PsiForStatement#getInitialization() 
 * @see PsiForStatement#getUpdate() 
 */
public interface PsiExpressionListStatement extends PsiStatement {
  /**
   * Returns the expression list contained in the statement.
   *
   * @return the expression list instance.
   */
  @NotNull PsiExpressionList getExpressionList();
}
