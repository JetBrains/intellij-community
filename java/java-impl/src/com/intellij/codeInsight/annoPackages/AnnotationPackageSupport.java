// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Support for custom annotation packages
 */
public interface AnnotationPackageSupport {
  /**
   * Returns nullability by a container annotation
   * @param anno annotation to check
   * @param types target types
   * @param superPackage if true then the annotation is applied to the super-package
   * @return NullabilityAnnotationInfo object if given annotation is recognized default annotation; null otherwise
   */
  @Nullable
  default NullabilityAnnotationInfo getNullabilityByContainerAnnotation(@NotNull PsiAnnotation anno,
                                                                        @NotNull PsiAnnotation.TargetType[] types,
                                                                        boolean superPackage) {
    return null;
  }

  /**
   * @param nullability desired nullability
   * @return list of explicit annotations which denote given nullability (and may denote additional semantics)
   */
  @NotNull
  default List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return Collections.emptyList();
  }

  /**
   * @param manager manager which wants to register annotations
   * @return array of available annotation packages
   */
  @NotNull
  static AnnotationPackageSupport[] getAnnotationPackages(@NotNull NullableNotNullManager manager) {
    AnnotationPackageSupport[] extensions = {
      new JetBrainsAnnotationSupport(), new FindBugsAnnotationSupport(), new AndroidAnnotationSupport(),
      new Jsr305Support(manager), new CheckerFrameworkSupport()
    };
    if (CodeAnalysisAnnotationSupport.IS_AVAILABLE) {
      return ArrayUtil.append(extensions, new CodeAnalysisAnnotationSupport());
    }
    return extensions;
  }
}
