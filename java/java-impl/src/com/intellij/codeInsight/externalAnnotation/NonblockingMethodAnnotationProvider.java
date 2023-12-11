// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.externalAnnotation.BlockingMethodAnnotationProvider.DEFAULT_BLOCKING_ANNOTATION;
import static com.intellij.codeInsight.externalAnnotation.BlockingMethodAnnotationProvider.DEFAULT_NONBLOCKING_ANNOTATION;

public final class NonblockingMethodAnnotationProvider implements AnnotationProvider {

  @NotNull
  @Override
  public String getName(Project project) {
    return DEFAULT_NONBLOCKING_ANNOTATION;
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return owner instanceof PsiMethod &&
           !owner.hasAnnotation(DEFAULT_BLOCKING_ANNOTATION);
  }
}
