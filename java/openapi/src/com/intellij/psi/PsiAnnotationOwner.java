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
   * E.g. Type annotations on method belong to its type element, not them method.
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
