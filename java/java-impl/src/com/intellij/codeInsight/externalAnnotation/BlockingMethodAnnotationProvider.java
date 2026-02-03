// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.externalAnnotation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;

public final class BlockingMethodAnnotationProvider implements AnnotationProvider {

  static final String DEFAULT_NONBLOCKING_ANNOTATION = "org.jetbrains.annotations.NonBlocking";
  static final String DEFAULT_BLOCKING_ANNOTATION = "org.jetbrains.annotations.Blocking";

  @Override
  public @NotNull String getName(Project project) {
    return DEFAULT_BLOCKING_ANNOTATION;
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return owner instanceof PsiMethod &&
           !owner.hasAnnotation(DEFAULT_NONBLOCKING_ANNOTATION);
  }
}
