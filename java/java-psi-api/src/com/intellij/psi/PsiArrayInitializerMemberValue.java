// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Represents an array used as a value of an annotation element. For example:
 * {@code @Endorsers({"Children", "Unscrupulous dentists"})}
 */
public interface PsiArrayInitializerMemberValue extends PsiAnnotationMemberValue {
  /**
   * Returns the list of elements in the initializer array.
   *
   * @return the initializer array elements.
   */
  PsiAnnotationMemberValue @NotNull [] getInitializers();

  /**
   * @return the number of elements in this array initializer expression
   */
  default int getInitializerCount() {
    return getInitializers().length;
  }

  /**
   * @return {@code true} if this array initializer member value contains no elements
   */
  default boolean isEmpty() {
    return getInitializerCount() == 0;
  }
}
