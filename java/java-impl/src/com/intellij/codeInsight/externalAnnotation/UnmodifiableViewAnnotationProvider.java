// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public String getName(Project project) {
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
