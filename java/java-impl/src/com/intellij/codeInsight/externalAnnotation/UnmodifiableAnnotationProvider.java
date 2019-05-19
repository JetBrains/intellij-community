// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class UnmodifiableAnnotationProvider implements AnnotationProvider {

  @NotNull
  @Override
  public String getName(Project project) {
    return Mutability.UNMODIFIABLE_ANNOTATION;
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return ApplicationManagerEx.getApplicationEx().isInternal() &&
           owner instanceof PsiMethod &&
           !ClassUtils.isImmutable(((PsiMethod)owner).getReturnType());
  }

  @NotNull
  @Override
  public String[] getAnnotationsToRemove(Project project) {
    return new String[]{Mutability.UNMODIFIABLE_VIEW_ANNOTATION};
  }
}
