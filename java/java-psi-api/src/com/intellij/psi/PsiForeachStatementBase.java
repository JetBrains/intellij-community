// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface PsiForeachStatementBase extends PsiLoopStatement {
  /**
   * Returns the expression representing the sequence over which the iteration is performed.
   *
   * @return the iterated value expression instance, or null if the statement is incomplete.
   */
  @Nullable
  PsiExpression getIteratedValue();

  /**
   * Returns the opening parenthesis enclosing the statement header.
   *
   * @return the opening parenthesis.
   */
  @NotNull
  PsiJavaToken getLParenth();

  /**
   * Returns the closing parenthesis enclosing the statement header.
   *
   * @return the closing parenthesis, or null if the statement is incomplete.
   */
  @Nullable
  PsiJavaToken getRParenth();
}
