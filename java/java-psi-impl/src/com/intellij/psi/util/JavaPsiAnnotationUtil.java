// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.PsiImplUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;

/**
 * @deprecated use com.intellij.java.codeserver.core.JavaPsiAnnotationUtil
 */
@ApiStatus.ScheduledForRemoval
@Deprecated
public final class JavaPsiAnnotationUtil {
  /**
   * @deprecated use com.intellij.java.codeserver.core.JavaPsiAnnotationUtil
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
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
