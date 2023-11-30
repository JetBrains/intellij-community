// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class AddTypeAnnotationFix extends PsiUpdateModCommandQuickFix {
  private final @NotNull String myAnnotationToAdd;
  private final @NotNull Collection<String> myAnnotationsToRemove;

  public AddTypeAnnotationFix(@NotNull String annotationToAdd, @NotNull Collection<String> annotationsToRemove) {
    myAnnotationToAdd = annotationToAdd;
    myAnnotationsToRemove = annotationsToRemove;
  }

  @Override
  public @NotNull String getName() {
    return JavaAnalysisBundle.message("inspection.i18n.quickfix.annotate.as", StringUtil.getShortName(myAnnotationToAdd));
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.add.type.annotation.family");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiTypeElement typeElement = ObjectUtils.tryCast(element, PsiTypeElement.class);
    if (typeElement == null || !typeElement.acceptsAnnotations()) return;
    for (PsiAnnotation annotation : typeElement.getAnnotations()) {
      if (myAnnotationsToRemove.contains(annotation.getQualifiedName())) {
        annotation.delete();
      }
    }
    typeElement.addAnnotation(myAnnotationToAdd);
  }
}
