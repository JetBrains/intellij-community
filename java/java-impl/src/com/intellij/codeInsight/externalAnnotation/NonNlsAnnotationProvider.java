// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class NonNlsAnnotationProvider implements AnnotationProvider {
  @NotNull
  @Override
  public String getName(Project project) {
    return "org.jetbrains.annotations.NonNls";
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return owner instanceof PsiMethod && isString(((PsiMethod)owner).getReturnType()) ||
           owner instanceof PsiVariable && isString(((PsiVariable)owner).getType());
  }

  private static boolean isString(PsiType type) {
    return type != null && TypeUtils.isJavaLangString(type.getDeepComponentType());
  }

  @NotNull
  @Override
  public String[] getAnnotationsToRemove(Project project) {
    return new String[]{"org.jetbrains.annotations.Nls"};
  }
}
