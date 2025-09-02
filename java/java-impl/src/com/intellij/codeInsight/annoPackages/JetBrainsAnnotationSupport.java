// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ContextNullabilityInfo;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

final class JetBrainsAnnotationSupport implements AnnotationPackageSupport {
  @Override
  public @NotNull ContextNullabilityInfo getNullabilityByContainerAnnotation(@NotNull PsiAnnotation anno,
                                                                             PsiAnnotation.TargetType @NotNull [] types,
                                                                             boolean superPackage) {
    if (superPackage) return ContextNullabilityInfo.EMPTY;
    if (ArrayUtil.contains(PsiAnnotation.TargetType.LOCAL_VARIABLE, types)) return ContextNullabilityInfo.EMPTY;
    if (!anno.hasQualifiedName(AnnotationUtil.NOT_NULL_BY_DEFAULT)) return ContextNullabilityInfo.EMPTY;
    if (ArrayUtil.contains(PsiAnnotation.TargetType.TYPE_PARAMETER, types)) {
      return ContextNullabilityInfo.constant(new NullabilityAnnotationInfo(anno, Nullability.NOT_NULL, true))
        .disableInCast()
        // Declared type parameter without a bound like <T> is equal to <T extends Object>, and the Object is implicitly annotated as NotNull
        .filtering(context -> context instanceof PsiTypeParameter typeParameter && typeParameter.getExtendsListTypes().length == 0);
    }
    return ContextNullabilityInfo.constant(new NullabilityAnnotationInfo(anno, Nullability.NOT_NULL, true))
      .disableInCast()
      .filtering(context -> !JSpecifyAnnotationSupport.resolvesToTypeParameter(context));
  }

  @Override
  public @NotNull List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Collections.singletonList(AnnotationUtil.NOT_NULL);
      case NULLABLE -> Collections.singletonList(AnnotationUtil.NULLABLE);
      case UNKNOWN -> Collections.singletonList(AnnotationUtil.UNKNOWN_NULLABILITY);
    };
  }

  @Override
  public boolean isTypeUseAnnotationLocationRestricted() {
    return true;
  }
}
