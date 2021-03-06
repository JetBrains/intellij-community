// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
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
  public static InferredAnnotationsManager getInstance(@NotNull Project project) {
    return project.getService(InferredAnnotationsManager.class);
  }

  /**
   * @return if exists, an inferred annotation by given qualified name on a given PSI element. Several invocations may return several
   * different instances of {@link PsiAnnotation}, which are not guaranteed to be equal.
   */
  @Nullable
  public abstract PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  /**
   * When annotation name is known, prefer {@link #findInferredAnnotation(PsiModifierListOwner, String)} as
   * potentially faster.
   *
   * @return all inferred annotations for the given element
   */
  public abstract PsiAnnotation @NotNull [] findInferredAnnotations(@NotNull PsiModifierListOwner listOwner);

  /**
   * @return whether the given annotation was inferred by this service.
   *
   * @see AnnotationUtil#isInferredAnnotation(PsiAnnotation)
   */
  public abstract boolean isInferredAnnotation(@NotNull PsiAnnotation annotation);
}
