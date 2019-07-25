// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code break} statement.
 */
public interface PsiBreakStatement extends PsiStatement {
  /** @deprecated the PSI structure has changed again since 2019.2; use {@link #getLabelIdentifier()} ()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Nullable PsiReferenceExpression getLabelExpression();

  /** @deprecated the PSI structure has changed again since 2019.2; use {@link #getLabelIdentifier()} ()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
  @Nullable PsiExpression getValueExpression();

  /** @deprecated the PSI structure has changed again since 2019.2; use {@link #getLabelIdentifier()} ()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
  @Nullable PsiExpression getExpression();

  /** @deprecated the PSI structure has changed again since 2019.2; use {@link #findExitedStatement()} ()} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Nullable PsiElement findExitedElement();

  /**
   * Returns an identifier element containing the statement's target label, if any.
   */
  default PsiIdentifier getLabelIdentifier() {
    PsiReferenceExpression expression = getLabelExpression();
    return expression != null ? PsiTreeUtil.getChildOfType(expression, PsiIdentifier.class) : null;
  }

  /**
   * Returns an instance of {@link PsiLoopStatement} or {@link PsiSwitchStatement}
   * representing the element out of which {@code break} transfers control.
   */
  default PsiStatement findExitedStatement() {
    PsiElement enclosingElement = findExitedElement();
    return enclosingElement instanceof PsiStatement ? (PsiStatement)enclosingElement : null;
  }
}