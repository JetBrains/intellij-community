// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a single switch label or labeled rule in a Java {@code switch} statement.
 *
 * @see PsiSwitchLabelStatement
 * @see PsiSwitchLabeledRuleStatement
 */
public interface PsiSwitchLabelStatementBase extends PsiStatement {
  /**
   * Returns {@code true} if the element represents a {@code default} section, {@code false} otherwise.
   */
  boolean isDefaultCase();

  /**
   * Returns the constant associated with the {@code case} block,
   * or {@code null} if the statement is incomplete or the element represents a {@code default} section.
   */
  @Nullable PsiExpression getCaseValue();

  /**
   * Returns the {@code switch} statement with which the section is associated,
   * or {@code null} if the element is not valid in its current context.
   */
  @Nullable PsiSwitchStatement getEnclosingSwitchStatement();
}