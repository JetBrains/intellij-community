// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiTypeElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class AddTypeAnnotationFix implements LocalQuickFix {
  private final @NotNull String myAnnotationToAdd;
  @SafeFieldForPreview
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
  public void applyFix(@NotNull Project project,
                       @NotNull ProblemDescriptor descriptor) {
    PsiTypeElement typeElement = ObjectUtils.tryCast(descriptor.getStartElement(), PsiTypeElement.class);
    if (typeElement == null || !typeElement.acceptsAnnotations()) return;
    for (PsiAnnotation annotation : typeElement.getAnnotations()) {
      if (myAnnotationsToRemove.contains(annotation.getQualifiedName())) {
        annotation.delete();
      }
    }
    typeElement.addAnnotation(myAnnotationToAdd);
  }
}
