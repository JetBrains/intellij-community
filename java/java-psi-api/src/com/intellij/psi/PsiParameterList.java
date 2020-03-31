/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @Nullable
  default PsiParameter getParameter(int index) {
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
