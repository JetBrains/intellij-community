// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
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
   * Returns the constants associated with the {@code case} block,
   * or {@code null} if the statement is incomplete.
   * @deprecated use {@link #getCaseLabelElementList()}
   */
  @Deprecated
  @Nullable PsiExpressionList getCaseValues();

  /** @deprecated doesn't support enhanced "switch" statements; use {@link #getCaseValues()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  default PsiExpression getCaseValue() {
    PsiExpressionList expressionList = getCaseValues();
    if (expressionList != null) {
      PsiExpression[] expressions = expressionList.getExpressions();
      if (expressions.length == 1) {
        return expressions[0];
      }
    }
    return null;
  }

  /**
   * Returns the {@code switch} block (a statement or an expression) with which the section is associated,
   * or {@code null} if the element is not valid in its current context.
   */
  @Nullable PsiSwitchBlock getEnclosingSwitchBlock();

  /** @deprecated doesn't support "switch" expressions; use {@link #getEnclosingSwitchBlock()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  default PsiSwitchStatement getEnclosingSwitchStatement() {
    PsiSwitchBlock block = getEnclosingSwitchBlock();
    return block instanceof PsiSwitchStatement ? (PsiSwitchStatement)block : null;
  }

  /**
   * @return list of case labels or null if it is incomplete
   */
  @Nullable PsiCaseLabelElementList getCaseLabelElementList();
}