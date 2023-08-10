// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class MoveAnnotationToArrayFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.family.name.move.annotation.to.array");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiAnnotation annotation = ObjectUtils.tryCast(element, PsiAnnotation.class);
    if (annotation == null) return;
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName == null) return;
    PsiModifierList owner = ObjectUtils.tryCast(annotation.getOwner(), PsiModifierList.class);
    if (owner == null) return;
    PsiModifierListOwner member = ObjectUtils.tryCast(owner.getParent(), PsiModifierListOwner.class);
    PsiTypeElement typeElement = member instanceof PsiMethod method ? method.getReturnTypeElement()
                   : member instanceof PsiVariable variable ? variable.getTypeElement() : null;
    if (typeElement == null || !(typeElement.getType() instanceof PsiArrayType)) return;
    PsiAnnotation addedAnnotation = typeElement.addAnnotation(qualifiedName);
    addedAnnotation.replace(annotation);
    annotation.delete();
  }
}
