// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;

public final class JavaPsiAnnotationUtil {
  /**
   * @param annotation annotation class
   * @return annotation retention policy; null if cannot be determined
   */
  public static @Nullable RetentionPolicy getRetentionPolicy(@NotNull PsiClass annotation) {
    PsiModifierList modifierList = annotation.getModifierList();
    if (modifierList != null) {
      PsiAnnotation retentionAnno = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION);
      if (retentionAnno == null) return RetentionPolicy.CLASS;

      PsiAnnotationMemberValue policyRef = PsiImplUtil.findAttributeValue(retentionAnno, null);
      if (policyRef instanceof PsiReference) {
        PsiElement field = ((PsiReference)policyRef).resolve();
        if (field instanceof PsiEnumConstant) {
          String name = ((PsiEnumConstant)field).getName();
          try {
            return Enum.valueOf(RetentionPolicy.class, name);
          }
          catch (IllegalArgumentException ignored) {
          }
        }
      }
    }

    return null;
  }
}
