// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.ContextNullabilityInfo;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Support for custom annotation packages.
 * The registration order of extension matters, as the annotations returned from the first {@link #getNullabilityAnnotations(Nullability)}
 * will be preferred by default.
 */
public interface AnnotationPackageSupport {
  ExtensionPointName<AnnotationPackageSupport> EP_NAME = ExtensionPointName.create("com.intellij.lang.jvm.annotationPackageSupport");

  /**
   * Returns a partially applied function that returns nullability by a container annotation, depending on context
   *
   * @param anno         annotation to check
   * @param types        target types
   * @param superPackage if true, then the annotation is applied to the super-package
   * @return {@code ContextNullabilityInfo} which returns nullability by a container annotation for a given context
   */
  default @NotNull ContextNullabilityInfo getNullabilityByContainerAnnotation(@NotNull PsiAnnotation anno,
                                                                              PsiAnnotation.TargetType @NotNull [] types,
                                                                              boolean superPackage) {
    return ContextNullabilityInfo.constant(null);
  }

  /**
   * @param nullability desired nullability
   * @return list of explicit annotations which denote given nullability (and may denote additional semantics). 
   * The annotation returned first will be preferred by default
   * in {@link NullableNotNullManager#getDefaultAnnotation(Nullability, PsiElement)}. 
   */
  default @NotNull List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return Collections.emptyList();
  }

  /**
   * @return true if the annotations defined by this support cannot be placed at wildcards or type parameters 
   */
  default boolean isTypeUseAnnotationLocationRestricted() {
    return false;
  }

  /**
   * @return true if the annotations defined by this support can be used to annotate local variables 
   */
  default boolean canAnnotateLocals() {
    return true;
  }
}
