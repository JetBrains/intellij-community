// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code continue} statement.
 */
public interface PsiContinueStatement extends PsiStatement {
  /**
   * Returns an identifier element containing the statement's target label, if any.
   */
  @Nullable PsiIdentifier getLabelIdentifier();

  /**
   * Returns the statement instance ({@link PsiForStatement}, {@link PsiWhileStatement} etc.) representing
   * the statement to the next iteration of which {@code continue} transfers control.
   */
  @Nullable PsiStatement findContinuedStatement();
}