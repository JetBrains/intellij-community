/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.lang.jvm.JvmTypeParametersOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a PSI element (class, interface, method or constructor) which can own a type
 * parameter list.
 *
 * @author dsl
 */
public interface PsiTypeParameterListOwner extends PsiMember, JvmTypeParametersOwner {
  /**
   * Checks if the element has any type parameters.
   *
   * @return true if the element has type parameters, false otherwise
   */
  boolean hasTypeParameters();

  /**
   * Returns the type parameter list for the element.
   *
   * @return the type parameter list, or null if the element has no type parameters.
   */
  @Nullable
  PsiTypeParameterList getTypeParameterList();

  /**
   * Returns the array of type parameters for the element.
   *
   * @return the array of type parameters, or an empty array if the element has no type parameters.
   */
  @NotNull
  @Override
  PsiTypeParameter[] getTypeParameters();
}
