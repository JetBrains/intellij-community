// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java local variable.
 */
public interface PsiLocalVariable extends PsiVariable {
  /**
   * Adds initializer to the variable declaration statement or, if {@code initializer}
   * parameter is null, removes initializer from variable.
   *
   * @param initializer the initializer to add.
   * @throws IncorrectOperationException if the modifications fails for some reason.
   */
  @Override
  void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException;

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  PsiTypeElement getTypeElement();

  @NotNull
  @Override
  @NlsSafe String getName();
}
