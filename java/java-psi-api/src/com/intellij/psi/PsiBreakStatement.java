// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code break} statement.
 */
public interface PsiBreakStatement extends PsiStatement {
  /**
   * Returns the label identifier iff it is present and the statement is not inside a switch expression, {@code null} otherwise.
   *
   * @see #getExpression()
   */
  @Nullable PsiIdentifier getLabelIdentifier();

  /**
   * Returns the label/value expression, or {@code null} if the statement is empty.
   */
  @Nullable PsiExpression getExpression();

  /**
   * Returns the statement ({@link PsiLoopStatement} or {@link PsiSwitchStatement}) or {@link PsiSwitchExpression switch expression}
   * representing the element out of which {@code break} transfers control.
   */
  @Nullable PsiElement findExitedElement();

  /** @deprecated doesn't support "switch" expressions; use {@link #findExitedElement()} instead */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  default PsiStatement findExitedStatement() {
    PsiElement enclosingElement = findExitedElement();
    return enclosingElement instanceof PsiStatement ? (PsiStatement)enclosingElement : null;
  }
}