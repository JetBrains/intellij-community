// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AddTypeAnnotationFix extends PsiUpdateModCommandAction<PsiTypeElement> {
  private final @NotNull String myAnnotationToAdd;
  private final @NotNull Collection<String> myAnnotationsToRemove;

  public AddTypeAnnotationFix(@NotNull PsiTypeElement element, @NotNull String annotationToAdd, @NotNull Collection<String> annotationsToRemove) {
    super(element);
    myAnnotationToAdd = annotationToAdd;
    myAnnotationsToRemove = annotationsToRemove;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiTypeElement element) {
    if (!element.acceptsAnnotations()) return null;
    return Presentation.of(JavaAnalysisBundle.message("inspection.i18n.quickfix.annotate.as", StringUtil.getShortName(myAnnotationToAdd)));
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.add.type.annotation.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiTypeElement typeElement, @NotNull ModPsiUpdater updater) {
    for (PsiAnnotation annotation : typeElement.getAnnotations()) {
      if (myAnnotationsToRemove.contains(annotation.getQualifiedName())) {
        annotation.delete();
      }
    }
    JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(typeElement.addAnnotation(myAnnotationToAdd));
  }
}
