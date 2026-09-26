// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a list of expressions separated by commas.
 *
 * @see PsiCall#getArgumentList()
 * @see PsiExpressionListStatement#getExpressionList()
 * @see PsiSwitchLabelStatementBase#getCaseValues()
 * @see PsiAnonymousClass#getArgumentList()
 */
public interface PsiExpressionList extends PsiElement {
  /**
   * @return the expressions contained in the list
   */
  PsiExpression @NotNull [] getExpressions();

  /**
   * @return the types of the expressions contained in this list.
   */
  PsiType @NotNull [] getExpressionTypes();

  /**
   * @return the number of expressions in this expression list
   */
  default int getExpressionCount() {
    return getExpressions().length;
  }

  /**
   * @return {@code true} if this expression list contains no expressions
   */
  default boolean isEmpty() {
    return getExpressionCount() == 0;
  }
}