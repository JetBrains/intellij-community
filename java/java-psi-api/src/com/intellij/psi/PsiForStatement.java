// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java basic {@code for} statement.
 */
public interface PsiForStatement extends PsiConditionalLoopStatement{
  /**
   * Returns the initialization part of the statement.
   *
   * @return the initialization part, or {@code null} if the statement is incomplete.
   */
  @Nullable
  PsiStatement getInitialization();

  /**
   * Returns the update part of the statement.
   *
   * @return the update part, or {@code null} if no update has been specified.
   */
  @Nullable
  PsiStatement getUpdate();

  /**
   * Returns the opening parenthesis enclosing the statement header.
   *
   * @return the opening parenthesis, or {@code null} if the statement is incomplete.
   */
  @Nullable
  PsiJavaToken getLParenth();

  /**
   * Returns the closing parenthesis enclosing the statement header.
   *
   * @return the closing parenthesis, or {@code null} if the statement is incomplete.
   */
  @Nullable
  PsiJavaToken getRParenth();
}
