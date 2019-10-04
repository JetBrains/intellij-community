// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code break} statement.
 */
public interface PsiBreakStatement extends PsiStatement {
  /**
   * Returns an identifier element containing the statement's target label, if any.
   */
  @Nullable PsiIdentifier getLabelIdentifier();

  /**
   * Returns an instance of {@link PsiLoopStatement} or {@link PsiSwitchStatement}
   * representing the element out of which {@code break} transfers control.
   */
  @Nullable PsiStatement findExitedStatement();
}