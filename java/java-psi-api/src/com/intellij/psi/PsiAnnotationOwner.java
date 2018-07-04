// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
   * @return the list of annotations which are applicable to this owner
   *         (e.g. type annotations on method belong to its type element, not the method).
   */
  @NotNull
  PsiAnnotation[] getApplicableAnnotations();

  /**
   * Searches the owner for an annotation with the specified fully qualified name
   * and returns one if it is found.
   *
   * @param qualifiedName the fully qualified name of the annotation to find.
   * @return the annotation instance, or null if no such annotation is found.
   */
  @Nullable
  PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName);

  /**
   * Searches the owner for an annotation with the specified fully qualified name
   * and returns {@code true} if it is found.
   * <p/>
   * This method is preferable over {@link #findAnnotation}
   * since implementations are free not to instantiate the {@link PsiAnnotation}.
   *
   * @param qualifiedName the fully qualified name of the annotation to find
   * @return {@code true} is such annotation is found, otherwise {@code false}
   * @since 2018.2
   */
  default boolean hasAnnotation(@NotNull @NonNls String qualifiedName) {
    //noinspection SSBasedInspection
    return findAnnotation(qualifiedName) != null;
  }

  /**
   * Adds a new annotation to this owner. The annotation class name will be shortened. No attributes will be defined.
   *
   * @param qualifiedName qualifiedName
   * @return newly added annotation
   */
  @NotNull
  PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName);
}
