// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.lang.jvm.types.JvmReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a type parameter.
 *
 * @see java.lang.reflect.TypeVariable
 */
public interface JvmTypeParameter extends JvmTypeDeclaration {

  /**
   * @return bounds of this type parameter
   * @see java.lang.reflect.TypeVariable#getBounds
   * @see java.lang.reflect.TypeVariable#getAnnotatedBounds
   */
  @NotNull
  JvmReferenceType[] getBounds();

  /**
   * @return the element which is parameterized by this type parameter
   * @see java.lang.reflect.TypeVariable#getGenericDeclaration
   */
  @Nullable
  JvmTypeParametersOwner getOwner();

  @Override
  default <T> T accept(@NotNull JvmElementVisitor<T> visitor) {
    return visitor.visitTypeParameter(this);
  }
}
