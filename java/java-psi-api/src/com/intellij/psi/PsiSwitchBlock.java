// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code switch} statement or {@code switch} expression.
 *
 * @see PsiSwitchStatement
 * @see PsiSwitchExpression
 */
public interface PsiSwitchBlock extends PsiElement {
  /**
   * Returns the selector expression on which the switch is performed, or {@code null} if the statement is incomplete.
   */
  @Nullable PsiExpression getExpression();

  /**
   * Returns the body of the switch statement, or {@code null} if the statement is incomplete.
   */
  @Nullable PsiCodeBlock getBody();

  @Nullable PsiJavaToken getLParenth();
  @Nullable PsiJavaToken getRParenth();
}