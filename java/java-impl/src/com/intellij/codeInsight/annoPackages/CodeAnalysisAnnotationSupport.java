// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiAnnotation;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CodeAnalysisAnnotationSupport implements AnnotationPackageSupport {
  private static final String PACKAGE_NAME = "codeanalysis.experimental.annotations";
  private static final String NULLABLE = PACKAGE_NAME + "." + "Nullable";
  private static final String NOT_NULL = PACKAGE_NAME + "." + "NotNull";
  private static final String NULLNESS_UNKNOWN = PACKAGE_NAME + "." + "NullnessUnknown";
  private static final String DEFAULT_NULLABLE = PACKAGE_NAME + "." + "DefaultNullable";
  private static final String DEFAULT_NOT_NULL = PACKAGE_NAME + "." + "DefaultNotNull";
  private static final String DEFAULT_NULLNESS_UNKNOWN = PACKAGE_NAME + "." + "DefaultNullnessUnknown";

  private static boolean isAvailable() {
    return Registry.is("java.codeanalysis.annotations.available");
  }

  @Nullable
  @Override
  public NullabilityAnnotationInfo getNullabilityByContainerAnnotation(@NotNull PsiAnnotation anno,
                                                                       PsiAnnotation.TargetType @NotNull [] types,
                                                                       boolean superPackage) {
    if (!isAvailable()) return null;
    if (superPackage) return null;
    String name = anno.getQualifiedName();
    if (name == null) return null;
    if (ArrayUtil.contains(PsiAnnotation.TargetType.LOCAL_VARIABLE, types)) return null;
    Nullability nullability;
    switch (name) {
      case DEFAULT_NULLABLE:
        nullability = Nullability.NULLABLE;
        break;
      case DEFAULT_NOT_NULL:
        nullability = Nullability.NOT_NULL;
        break;
      case DEFAULT_NULLNESS_UNKNOWN:
        nullability = Nullability.UNKNOWN;
        break;
      default:
        return null;
    }
    return new NullabilityAnnotationInfo(anno, nullability, true);
  }
  
  @NotNull
  @Override
  public List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    if (!isAvailable()) {
      return Collections.emptyList();
    }
    switch (nullability) {
      case NOT_NULL:
        return Collections.singletonList(NOT_NULL);
      case NULLABLE:
        return Collections.singletonList(NULLABLE);
      default:
        return Collections.singletonList(NULLNESS_UNKNOWN);
    }
  }
}
