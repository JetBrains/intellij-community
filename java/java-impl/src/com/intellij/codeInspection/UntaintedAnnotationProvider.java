// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.externalAnnotation.AnnotationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;

public class UntaintedAnnotationProvider implements AnnotationProvider {

  public static final String DEFAULT_UNTAINTED_ANNOTATION = "org.checkerframework.checker.tainting.qual.Untainted";
  public static final String DEFAULT_TAINTED_ANNOTATION = "org.checkerframework.checker.tainting.qual.Tainted";
  public static final String DEFAULT_POLY_TAINTED_ANNOTATION = "org.checkerframework.checker.tainting.qual.PolyTainted";

  @Override
  public @NotNull String getName(Project project) {
    return DEFAULT_UNTAINTED_ANNOTATION;
  }

  @Override
  public boolean isAvailable(PsiModifierListOwner owner) {
    return (owner instanceof PsiMethod || owner instanceof PsiLocalVariable) &&
           !owner.hasAnnotation(DEFAULT_TAINTED_ANNOTATION) &&
           !owner.hasAnnotation(DEFAULT_POLY_TAINTED_ANNOTATION);
  }
}
