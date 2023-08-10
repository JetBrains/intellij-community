// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Support for custom annotation packages
 */
public interface AnnotationPackageSupport {
  ExtensionPointName<AnnotationPackageSupport> EP_NAME = ExtensionPointName.create("com.intellij.lang.jvm.annotationPackageSupport");
  
  /**
   * Returns nullability by a container annotation
   *
   * @param anno         annotation to check
   * @param context      target PsiElement (usually, method or variable)
   * @param types        target types
   * @param superPackage if true then the annotation is applied to the super-package
   * @return NullabilityAnnotationInfo object if given annotation is recognized default annotation; null otherwise
   */
  default @Nullable NullabilityAnnotationInfo getNullabilityByContainerAnnotation(@NotNull PsiAnnotation anno,
                                                                                  @NotNull PsiElement context,
                                                                                  PsiAnnotation.TargetType @NotNull [] types,
                                                                                  boolean superPackage) {
    return null;
  }

  /**
   * @param nullability desired nullability
   * @return list of explicit annotations which denote given nullability (and may denote additional semantics)
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
