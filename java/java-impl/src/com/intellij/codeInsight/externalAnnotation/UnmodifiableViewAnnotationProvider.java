// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public final class UnmodifiableViewAnnotationProvider implements AnnotationProvider {

  @Override
  public @NotNull String getName(Project project) {
    return Mutability.UNMODIFIABLE_VIEW_ANNOTATION;
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    if (!(owner instanceof PsiMethod)) return false;
    PsiClass returnClass = PsiUtil.resolveClassInClassTypeOnly(((PsiMethod)owner).getReturnType());
    return InheritanceUtil.isInheritor(returnClass, CommonClassNames.JAVA_UTIL_COLLECTION) ||
           InheritanceUtil.isInheritor(returnClass, CommonClassNames.JAVA_UTIL_MAP);
  }

  @Override
  public String @NotNull [] getAnnotationsToRemove(Project project) {
    return new String[]{Mutability.UNMODIFIABLE_ANNOTATION};
  }
}
