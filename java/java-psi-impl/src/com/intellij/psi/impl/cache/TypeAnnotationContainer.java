// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache;

import com.intellij.psi.PsiElement;
import com.intellij.psi.TypeAnnotationProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A container of all type annotations for given type, including structural children of the type
 * (type arguments, wildcard bounds, outer types, array element types)
 */
@ApiStatus.NonExtendable
public interface TypeAnnotationContainer {
  /**
   * A container that contains no type annotations.
   */
  TypeAnnotationContainer EMPTY = new TypeAnnotationContainer() {
    @Override
    public @NotNull TypeAnnotationContainer forArrayElement() {
      return this;
    }

    @Override
    public @NotNull TypeAnnotationContainer forEnclosingClass() {
      return this;
    }

    @Override
    public @NotNull TypeAnnotationContainer forBound() {
      return this;
    }

    @Override
    public @NotNull TypeAnnotationContainer forTypeArgument(int index) {
      return this;
    }

    @Override
    public @NotNull TypeAnnotationProvider getProvider(PsiElement parent) {
      return TypeAnnotationProvider.EMPTY;
    }

    @Override
    public void appendImmediateText(@NotNull StringBuilder sb) {
    }
  };

  /**
   * @return a derived container that contains annotations for an array element,
   * assuming that this container is used for the array
   */
  @NotNull TypeAnnotationContainer forArrayElement();

  /**
   * @return a derived container that contains annotations for enclosing class,
   * assuming that this container is used for the inner class 
   */
  @NotNull TypeAnnotationContainer forEnclosingClass();

  /**
   * @return type annotation container for wildcard bound
   * (assuming that this type annotation container is used for the bounded wildcard type)
   */
  @NotNull TypeAnnotationContainer forBound();

  /**
   * Returns a type annotation container for the given type argument index.
   * This is used for types that have type arguments, and it provides the
   * annotations associated with a specific type argument.
   *
   * @param index type argument index, zero-based
   * @return type annotation container for given type argument
   * (assuming that this type annotation container is used for a class type with type arguments)
   */
  @NotNull TypeAnnotationContainer forTypeArgument(int index);

  /**
   * @param parent parent PSI element for context
   * @return TypeAnnotationProvider
   */
  @NotNull TypeAnnotationProvider getProvider(PsiElement parent);

  /**
   * Appends to StringBuilder annotation text that applicable to this element immediately (not to sub-elements)
   */
  void appendImmediateText(@NotNull StringBuilder sb);
}
