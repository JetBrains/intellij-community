// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.externalAnnotation.BlockingMethodAnnotationProvider.DEFAULT_BLOCKING_ANNOTATION;
import static com.intellij.codeInsight.externalAnnotation.BlockingMethodAnnotationProvider.DEFAULT_NONBLOCKING_ANNOTATION;

public final class NonblockingMethodAnnotationProvider implements AnnotationProvider {

  @Override
  public @NotNull String getName(Project project) {
    return DEFAULT_NONBLOCKING_ANNOTATION;
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return owner instanceof PsiMethod &&
           !owner.hasAnnotation(DEFAULT_BLOCKING_ANNOTATION);
  }
}
