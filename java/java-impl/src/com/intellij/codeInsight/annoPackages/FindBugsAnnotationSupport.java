// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

final class FindBugsAnnotationSupport implements AnnotationPackageSupport {
  private static final String NON_NULL = "edu.umd.cs.findbugs.annotations.NonNull";
  private static final String NULLABLE = "edu.umd.cs.findbugs.annotations.Nullable";
  private static final String DEFAULT_ANNOTATION_FOR_PARAMETERS = "edu.umd.cs.findbugs.annotations.DefaultAnnotationForParameters";

  @NotNull
  @Override
  public List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Collections.singletonList(NON_NULL);
      case NULLABLE -> Collections.singletonList(NULLABLE);
      default -> Collections.emptyList();
    };
  }

  @Override
  public @Nullable NullabilityAnnotationInfo getNullabilityByContainerAnnotation(@NotNull PsiAnnotation anno,
                                                                                 @NotNull PsiElement context,
                                                                                 PsiAnnotation.TargetType @NotNull [] types,
                                                                                 boolean superPackage) {
    if (!superPackage && 
        anno.hasQualifiedName(DEFAULT_ANNOTATION_FOR_PARAMETERS) && 
        ArrayUtil.contains(PsiAnnotation.TargetType.PARAMETER, types)) {
      PsiAnnotationMemberValue value = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value instanceof PsiClassObjectAccessExpression cls &&
          cls.getOperand().getType() instanceof PsiClassType clsType &&
          ("NonNull".equals(clsType.getClassName()) || "Nullable".equals(clsType.getClassName()))) {
        PsiClass resolved = clsType.resolve();
        if (resolved != null) {
          String qualifiedName = resolved.getQualifiedName();
          if (NON_NULL.equals(qualifiedName)) {
            return new NullabilityAnnotationInfo(anno, Nullability.NOT_NULL, true);
          }
          if (NULLABLE.equals(qualifiedName)) {
            return new NullabilityAnnotationInfo(anno, Nullability.NULLABLE, true);
          }
        }
      }
    }
    return null;
  }
}
