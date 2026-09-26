// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Represents an array initializer expression.
 *
 * @see PsiNewExpression#getArrayInitializer() 
 */
public interface PsiArrayInitializerExpression extends PsiExpression {
  /**
   * Returns the expressions initializing the elements of the array.
   *
   * @return an array of initializer expressions.
   */
  PsiExpression @NotNull [] getInitializers();

  /**
   * @return the number of initializer expressions in this array initializer expression
   */
  default int getInitializerCount() {
    return getInitializers().length;
  }

  /**
   * @return {@code true} if this array initializer expression contains no expressions
   */
  default boolean isEmpty() {
    return getInitializerCount() == 0;
  }
}
