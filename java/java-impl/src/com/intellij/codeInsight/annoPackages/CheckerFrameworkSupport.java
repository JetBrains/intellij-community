// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

final class CheckerFrameworkSupport implements AnnotationPackageSupport {
  private static final String DEFAULT_QUALIFIER = "org.checkerframework.framework.qual.DefaultQualifier";
  private static final String DEFAULT_QUALIFIERS = "org.checkerframework.framework.qual.DefaultQualifiers";

  @Override
  public @NotNull ContextNullabilityInfo getNullabilityByContainerAnnotation(@NotNull PsiAnnotation anno,
                                                                                 PsiAnnotation.TargetType @NotNull [] types,
                                                                                 boolean superPackage) {
    if (anno.hasQualifiedName(DEFAULT_QUALIFIER)) {
      PsiAnnotationMemberValue value = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value instanceof PsiClassObjectAccessExpression &&
          hasAppropriateTarget(types, anno.findAttributeValue("locations"))) {
        PsiClass valueClass = PsiUtil.resolveClassInClassTypeOnly(((PsiClassObjectAccessExpression)value).getOperand().getType());
        NullabilityAnnotationInfo result = null;
        if (valueClass != null) {
          NullableNotNullManager instance = NullableNotNullManager.getInstance(value.getProject());
          if (instance.getNullables().contains(valueClass.getQualifiedName())) {
            result = new NullabilityAnnotationInfo(anno, Nullability.NULLABLE, true);
          }
          else if (instance.getNotNulls().contains(valueClass.getQualifiedName())) {
            result = new NullabilityAnnotationInfo(anno, Nullability.NOT_NULL, true);
          }
        }
        if (result == null) return ContextNullabilityInfo.EMPTY;
        return ContextNullabilityInfo.constant(result)
          .filtering(context -> {
            // DefaultQualifier is not applicable to type parameter declarations
            if (context instanceof PsiTypeParameter) return false;
            // DefaultQualifier is not applicable to type parameter uses
            return !(PsiUtil.getTypeByPsiElement(context) instanceof PsiClassType classType) || 
                   !(classType.resolve() instanceof PsiTypeParameter);
          });
      }
      return ContextNullabilityInfo.EMPTY;
    }

    if (anno.hasQualifiedName(DEFAULT_QUALIFIERS)) {
      PsiAnnotationMemberValue value = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      return StreamEx.of(AnnotationUtil.arrayAttributeValues(value))
        .select(PsiAnnotation.class)
        .map(initializer -> getNullabilityByContainerAnnotation(initializer, types, superPackage))
        .reduce(ContextNullabilityInfo::orElse)
        .orElse(ContextNullabilityInfo.EMPTY);
    }
    return ContextNullabilityInfo.EMPTY;
  }

  private static boolean hasAppropriateTarget(PsiAnnotation.TargetType[] types, PsiAnnotationMemberValue locations) {
    Set<String> locationNames = ContainerUtil.map2SetNotNull(AnnotationUtil.arrayAttributeValues(locations),
                                                             l -> l instanceof PsiReferenceExpression ? ((PsiReferenceExpression)l)
                                                               .getReferenceName() : null);
    if (locationNames.contains("ALL")) return true;
    for (PsiAnnotation.TargetType type : types) {
      if (type == PsiAnnotation.TargetType.FIELD) return locationNames.contains("FIELD");
      if (type == PsiAnnotation.TargetType.METHOD) return locationNames.contains("RETURN");
      if (type == PsiAnnotation.TargetType.PARAMETER) return locationNames.contains("PARAMETER");
      if (type == PsiAnnotation.TargetType.LOCAL_VARIABLE) return locationNames.contains("LOCAL_VARIABLE");
    }
    return false;
  }

  @Override
  public @NotNull List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Arrays.asList("org.checkerframework.checker.nullness.qual.NonNull",
                                     "org.checkerframework.checker.nullness.compatqual.NonNullDecl",
                                     "org.checkerframework.checker.nullness.compatqual.NonNullType");
      case NULLABLE -> Arrays.asList("org.checkerframework.checker.nullness.qual.Nullable",
                                     "org.checkerframework.checker.nullness.compatqual.NullableDecl",
                                     "org.checkerframework.checker.nullness.compatqual.NullableType");
      default -> Collections.singletonList("org.checkerframework.checker.nullness.qual.MonotonicNonNull");
    };
  }
}
