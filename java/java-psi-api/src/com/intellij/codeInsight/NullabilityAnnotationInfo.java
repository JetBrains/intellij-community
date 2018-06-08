// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.psi.PsiAnnotation;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a particular nullability annotation instance
 */
public class NullabilityAnnotationInfo {
  private final @NotNull PsiAnnotation myAnnotation;
  private final @NotNull Nullability myNullability;
  private final boolean myContainer;

  NullabilityAnnotationInfo(@NotNull PsiAnnotation annotation, @NotNull Nullability nullability, boolean container) {
    myAnnotation = annotation;
    myNullability = nullability;
    myContainer = container;
  }

  /**
   * @return annotation object (might be synthetic)
   */
  @NotNull
  public PsiAnnotation getAnnotation() {
    return myAnnotation;
  }

  /**
   * @return nullability this annotation represents
   */
  @NotNull
  public Nullability getNullability() {
    return myNullability;
  }

  /**
   * @return true if this annotation is a container annotation (applied to the whole class/package/etc.)
   */
  public boolean isContainer() {
    return myContainer;
  }
}
