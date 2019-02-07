// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a single {@code case} or {@code default} labeled rule in an "enhanced" Java {@code switch} statement.
 *
 * @see PsiSwitchLabelStatement
 */
public interface PsiSwitchLabeledRuleStatement extends PsiSwitchLabelStatementBase {
  /**
   * Returns a body of the rule: one of {@link PsiBlockStatement}, {@link PsiThrowStatement}, {@link PsiExpressionStatement}, or {@code null}.
   */
  @Nullable PsiStatement getBody();
}