// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code break} statement.
 */
public interface PsiBreakStatement extends PsiStatement {
  /**
   * Returns the label expression iff it is present, is an unqualified reference, and the statement is not inside a switch expression,
   * {@code null} otherwise.
   */
  @Nullable PsiReferenceExpression getLabelExpression();

  /**
   * Returns the value expression iff it is present and the statement is inside a switch expression, {@code null} otherwise.
   */
  @Nullable PsiExpression getValueExpression();

  /**
   * Returns the label or value expression, or {@code null} if the statement is empty.
   *
   * @see #getLabelExpression()
   * @see #getValueExpression()
   */
  @Nullable PsiExpression getExpression();

  /**
   * Returns the statement ({@link PsiLoopStatement} or {@link PsiSwitchStatement}) or {@link PsiSwitchExpression switch expression}
   * representing the element out of which {@code break} transfers control.
   */
  @Nullable PsiElement findExitedElement();

  /** @deprecated the PSI structure has changed since 2019.1; use {@link #getLabelExpression()} instead */
  @Deprecated
  default PsiIdentifier getLabelIdentifier() {
    PsiReferenceExpression expression = getLabelExpression();
    return expression != null ? PsiTreeUtil.getChildOfType(expression, PsiIdentifier.class) : null;
  }

  /** @deprecated doesn't support switch expressions; use {@link #findExitedElement()} instead */
  @Deprecated
  default PsiStatement findExitedStatement() {
    PsiElement enclosingElement = findExitedElement();
    return enclosingElement instanceof PsiStatement ? (PsiStatement)enclosingElement : null;
  }
}