/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.ArrayFactory;
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
   * Returns true when variable is declared as <code>var name;</code>
   * 
   * The actual type should be inferred according to the JEP 286: Local-Variable Type Inference
   * (http://openjdk.java.net/jeps/286). 
   * 
   * Applicable to local variables with initializers, foreach parameters, try-with-resources variables 
   */
  default boolean isInferredType() {
    return false;
  }
}