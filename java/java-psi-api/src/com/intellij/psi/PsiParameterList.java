// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the list of parameters of a Java method.
 *
 * @see PsiMethod#getParameterList()
 */
public interface PsiParameterList extends PsiElement {
  /**
   * Returns the array of parameters in the list (excluding type annotation receiver).
   */
  PsiParameter @NotNull [] getParameters();

  /**
   * Returns the index of the specified parameter in the list.
   *
   * @param parameter the parameter to search for (must belong to this parameter list).
   * @return the index of the parameter.
   */
  int getParameterIndex(@NotNull PsiParameter parameter);

  /**
   * Returns the number of parameters (excluding type annotation receiver).
   */
  int getParametersCount();

  /**
   * Returns the parameter by index
   * 
   * @param index parameter index, non-negative
   * @return parameter, or null if there are less parameters than the index supplied
   */
  default @Nullable PsiParameter getParameter(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("index is negative: " + index);
    }
    PsiParameter[] parameters = getParameters();
    return index < parameters.length ? parameters[index] : null;
  }

  /**
   * @return true if this parameter list has no parameters (excluding type annotation receiver).
   */
  default boolean isEmpty() {
    return getParametersCount() == 0;
  }
}
