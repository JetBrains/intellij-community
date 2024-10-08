// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

final class JetBrainsAnnotationSupport implements AnnotationPackageSupport {
  @Override
  public @Nullable NullabilityAnnotationInfo getNullabilityByContainerAnnotation(@NotNull PsiAnnotation anno,
                                                                                 @NotNull PsiElement context,
                                                                                 PsiAnnotation.TargetType @NotNull [] types,
                                                                                 boolean superPackage) {
    if (superPackage) return null;
    if (ArrayUtil.contains(PsiAnnotation.TargetType.LOCAL_VARIABLE, types)) return null;
    if (!anno.hasQualifiedName(AnnotationUtil.NOT_NULL_BY_DEFAULT)) return null;
    PsiExpression parentExpression = PsiTreeUtil.getParentOfType(context, PsiExpression.class);
    if (parentExpression instanceof PsiTypeCastExpression cast && PsiTreeUtil.isAncestor(cast.getCastType(), context, false)) {
      return null;
    }
    if (ArrayUtil.contains(PsiAnnotation.TargetType.TYPE_PARAMETER, types)) {
      if (context instanceof PsiTypeParameter typeParameter && typeParameter.getExtendsListTypes().length == 0) {
        // Declared type parameter without a bound like <T> is equal to <T extends Object>, and the Object is implicitly annotated as NotNull
        return new NullabilityAnnotationInfo(anno, Nullability.NOT_NULL, true);
      }
      return null;
    }
    if (JSpecifyAnnotationSupport.resolvesToTypeParameter(context)) return null;
    return new NullabilityAnnotationInfo(anno, Nullability.NOT_NULL, true);
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
