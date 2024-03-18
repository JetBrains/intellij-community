// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public final class NonNlsAnnotationProvider implements AnnotationProvider {
  @NotNull
  @Override
  public String getName(Project project) {
    return AnnotationUtil.NON_NLS;
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return owner instanceof PsiMethod && isAssignableFromString(((PsiMethod)owner).getReturnType(), owner) ||
           owner instanceof PsiVariable && isAssignableFromString(((PsiVariable)owner).getType(), owner);
  }

  private static boolean isAssignableFromString(PsiType type, PsiElement context) {
    if (type == null) {
      return false;
    }
    return type.getDeepComponentType().isAssignableFrom(TypeUtils.getStringType(context));
  }

  @Override
  public String @NotNull [] getAnnotationsToRemove(Project project) {
    return new String[]{AnnotationUtil.NLS};
  }
}
