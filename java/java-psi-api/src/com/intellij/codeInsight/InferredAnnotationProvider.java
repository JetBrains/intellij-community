// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Returns annotations inferred by bytecode or source code, for example contracts and nullity.
 * Don't invoke these extensions directly, use {@link InferredAnnotationsManager} instead.
 */
public interface InferredAnnotationProvider {
  ExtensionPointName<InferredAnnotationProvider> EP_NAME = ExtensionPointName.create("com.intellij.lang.inferredAnnotationProvider");

  /**
   * @return if exists, an inferred annotation by given qualified name on a given PSI element. Several invocations may return several
   * different instances of {@link PsiAnnotation}, which are not guaranteed to be equal.
   */
  @Nullable
  PsiAnnotation findInferredAnnotation(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN);

  /**
   * When annotation name is known, prefer {@link #findInferredAnnotation(PsiModifierListOwner, String)} as
   * potentially faster.
   *
   * @return all inferred annotations for the given element.
   */
  @NotNull
  List<PsiAnnotation> findInferredAnnotations(@NotNull PsiModifierListOwner listOwner);

}
