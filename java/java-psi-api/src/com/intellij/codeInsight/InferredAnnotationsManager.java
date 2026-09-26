// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Returns annotations inferred by bytecode or source code, for example contracts and nullity.
 *
 * @see NullableNotNullManager
 * @see Contract
 * @see Nullable
 * @see NotNull
 * @see AnnotationUtil
 */
public abstract class InferredAnnotationsManager {
  protected static final Key<Boolean> INFERRED_ANNOTATION = Key.create("INFERRED_ANNOTATION");

  @Contract(pure = true)
  public static InferredAnnotationsManager getInstance(@NotNull Project project) {
    return project.getService(InferredAnnotationsManager.class);
  }

  /**
   * @return if exists, an inferred annotation by given qualified name on a given PSI element. Several invocations may return several
   * different instances of {@link PsiAnnotation}, which are not guaranteed to be equal.
   */
  public abstract @Nullable PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  /**
   * When annotation name is known, prefer {@link #findInferredAnnotation(PsiModifierListOwner, String)} as
   * potentially faster.
   *
   * @return all inferred annotations for the given element
   */
  public abstract PsiAnnotation @NotNull [] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner);

  /**
   * @param annotation annotation to check
   * @return true if this annotation is inferred
   */
  public static boolean isInferredAnnotation(@NotNull PsiAnnotation annotation) {
    return annotation.getUserData(INFERRED_ANNOTATION) != null;
  }
}
