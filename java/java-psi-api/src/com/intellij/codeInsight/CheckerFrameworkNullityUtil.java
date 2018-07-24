// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author peter
 */
class CheckerFrameworkNullityUtil {
  private static final String DEFAULT_QUALIFIER = "org.checkerframework.framework.qual.DefaultQualifier";
  private static final String DEFAULT_QUALIFIERS = "org.checkerframework.framework.qual.DefaultQualifiers";

  @Nullable
  static NullabilityAnnotationInfo isCheckerDefault(PsiAnnotation anno, PsiAnnotation.TargetType[] types) {
    String qName = anno.getQualifiedName();
    if (DEFAULT_QUALIFIER.equals(qName)) {
      PsiAnnotationMemberValue value = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value instanceof PsiClassObjectAccessExpression &&
          hasAppropriateTarget(types, anno.findAttributeValue("locations"))) {
        PsiClass valueClass = PsiUtil.resolveClassInClassTypeOnly(((PsiClassObjectAccessExpression)value).getOperand().getType());
        if (valueClass != null) {
          NullableNotNullManager instance = NullableNotNullManager.getInstance(value.getProject());
          if (instance.getNullables().contains(valueClass.getQualifiedName())) {
            return new NullabilityAnnotationInfo(anno, Nullability.NULLABLE, true);
          }
          if (instance.getNotNulls().contains(valueClass.getQualifiedName())) {
            return new NullabilityAnnotationInfo(anno, Nullability.NULLABLE, true);
          }
        }
      }
      return null;
    }
    
    if (DEFAULT_QUALIFIERS.equals(qName)) {
      PsiAnnotationMemberValue value = anno.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      for (PsiAnnotationMemberValue initializer : AnnotationUtil.arrayAttributeValues(value)) {
        if (initializer instanceof PsiAnnotation) {
          NullabilityAnnotationInfo result = isCheckerDefault((PsiAnnotation)initializer, types);
          if (result != null) {
            return result;
          }
        }
      }
    }
    return null;
  }

  private static boolean hasAppropriateTarget(PsiAnnotation.TargetType[] types, PsiAnnotationMemberValue locations) {
    Set<String> locationNames = ContainerUtil.map2SetNotNull(AnnotationUtil.arrayAttributeValues(locations), l -> l instanceof PsiReferenceExpression ? ((PsiReferenceExpression)l).getReferenceName() : null);
    if (locationNames.contains("ALL")) return true;
    for (PsiAnnotation.TargetType type : types) {
      if (type == PsiAnnotation.TargetType.FIELD) return locationNames.contains("FIELD");
      if (type == PsiAnnotation.TargetType.METHOD) return locationNames.contains("RETURN");
      if (type == PsiAnnotation.TargetType.PARAMETER) return locationNames.contains("PARAMETER");
    }
    return false;
  }
}
