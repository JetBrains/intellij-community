/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * Represents a list of generic type parameters for a class or method.
 *
 * @author dsl
 */
public interface PsiTypeParameterList extends PsiElement {
  /**
   * Returns the array of type parameters contained in the list.
   *
   * @return the array of type parameters.
   */
  PsiTypeParameter[] getTypeParameters();

  /**
   * Returns the index of the specified parameter in the list.
   *
   * @param typeParameter the parameter to find.
   * @return the index of the parameter.
   */
  int getTypeParameterIndex (PsiTypeParameter typeParameter);
}
