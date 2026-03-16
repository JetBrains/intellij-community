// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveAnnotationToBoundFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.family.name.move.annotation.to.upper.bound");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiAnnotation annotation = ObjectUtils.tryCast(element, PsiAnnotation.class);
    if (annotation == null) return;
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName == null) return;
    PsiWildcardType owner = ObjectUtils.tryCast(annotation.getOwner(), PsiWildcardType.class);
    if (owner == null) return;
    PsiTypeElement typeElement = ObjectUtils.tryCast(annotation.getParent(), PsiTypeElement.class);
    if (typeElement == null) return;
    StringBuilder newText = new StringBuilder();
    boolean added = false;
    for (@NotNull PsiElement child : typeElement.getChildren()) {
      if (child == annotation) continue;
      newText.append(child.getText());
      if (PsiUtil.isJavaToken(child, JavaTokenType.EXTENDS_KEYWORD) || PsiUtil.isJavaToken(child, JavaTokenType.SUPER_KEYWORD)) {
        newText.append(" ").append(annotation.getText());
        added = true;
      }
    }
    if (!added) {
      newText.append(" extends ").append(annotation.getText()).append(" ").append(CommonClassNames.JAVA_LANG_OBJECT);
    }
    PsiTypeElement newTypeElement = JavaPsiFacade.getElementFactory(project).createTypeElementFromText(newText.toString().trim(), typeElement);
    typeElement.replace(newTypeElement);
  }

  static @Nullable MoveAnnotationToBoundFix create(@NotNull PsiAnnotation annotation) {
    PsiWildcardType owner = ObjectUtils.tryCast(annotation.getOwner(), PsiWildcardType.class);
    if (owner == null) return null;
    PsiType bound = owner.getBound();
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName == null) return null;
    if (bound != null && bound.hasAnnotation(qualifiedName)) return null;
    return new MoveAnnotationToBoundFix();
  }
}
