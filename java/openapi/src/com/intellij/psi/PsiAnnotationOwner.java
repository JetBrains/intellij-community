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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public interface PsiAnnotationOwner {
  /**
   * Returns the list of annotations syntactically contained in the element.
   *
   * @return the list of annotations.
   */
  @NotNull
  PsiAnnotation[] getAnnotations();


  /**
   * @return the list of annotations which are applicable to this owner.
   * E.g. Type annotations on method belong to its type element, not the method.
   */
  @NotNull PsiAnnotation[] getApplicableAnnotations();

  /**
   * Searches the modifier list for an annotation with the specified fully qualified name
   * and returns one if it is found.
   *
   * @param qualifiedName the fully qualified name of the annotation to find.
   * @return the annotation instance, or null if no such annotation is found.
   */
  @Nullable
  PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName);

  /**
   * Add a new annotation to this modifier list. The annotation class name will be shortened. No attribbutes will be defined.
   * @param qualifiedName qualifiedName
   * @return newly added annotation
   */
  @NotNull
  PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName);
}
