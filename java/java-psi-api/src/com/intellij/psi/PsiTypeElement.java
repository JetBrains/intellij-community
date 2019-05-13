// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the occurrence of a type in Java source code, for example, as a return
 * type of the method or the type of a method parameter.
 */
public interface PsiTypeElement extends PsiElement, PsiAnnotationOwner {
  /**
   * The empty array of PSI directories which can be reused to avoid unnecessary allocations.
   */
  PsiTypeElement[] EMPTY_ARRAY = new PsiTypeElement[0];

  ArrayFactory<PsiTypeElement> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiTypeElement[count];

  /**
   * Returns the type referenced by the type element.
   * <p>
   * Note: when a containing element (field, method etc.) has C-style array declarations,
   * the result of this method may differ from an actual type.
   *
   * @return the referenced type.
   * @see PsiField#getType()
   * @see PsiMethod#getReturnType()
   * @see PsiParameter#getType()
   * @see PsiVariable#getType()
   */
  @NotNull
  @Contract(pure = true)
  PsiType getType();

  /**
   * Returns the reference element pointing to the referenced type, or if the type element
   * is an array, the reference element for the innermost component type of the array.
   *
   * @return the referenced element instance, or null if the type element references
   * a primitive type.
   */
  @Nullable
  PsiJavaCodeReferenceElement getInnermostComponentReferenceElement();


  /**
   * Returns {@code true} when a variable is declared as {@code var name;}
   * 
   * The actual type should be inferred according to the JEP 286: Local-Variable Type Inference
   * (http://openjdk.java.net/jeps/286). 
   * 
   * Applicable to local variables with initializers, foreach parameters, try-with-resources variables 
   */
  @Contract(pure = true)
  default boolean isInferredType() {
    return false;
  }
}