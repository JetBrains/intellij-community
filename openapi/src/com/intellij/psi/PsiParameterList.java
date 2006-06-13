/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

/**
 * Represents the list of parameters of a Java method.
 *
 * @see PsiMethod#getParameterList()
 */
public interface PsiParameterList extends PsiElement {

  /**
   * Returns the array of parameters in the list.
   *
   * @return the array of parameters.
   */
  @NotNull
  PsiParameter[] getParameters();

  /**
   * Returns the index of the specified parameter in the list.
   *
   * @param parameter the parameter to search for (must belong to this parameter list).
   * @return the index of the parameter.
   */
  int getParameterIndex(PsiParameter parameter);

  /**
   * Returns the number of parameters.
   *
   * @return the parameters count
   */
  int getParametersCount();
}
