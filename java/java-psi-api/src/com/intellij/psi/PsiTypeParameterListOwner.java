// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public interface PsiTypeParameterListOwner extends PsiJvmMember, JvmTypeParametersOwner {
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
