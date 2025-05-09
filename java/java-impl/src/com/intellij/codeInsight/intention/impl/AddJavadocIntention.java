// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.editorActions.FixDocCommentAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.ide.util.PackageUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddJavadocIntention extends PsiUpdateModCommandAction<PsiElement> {
  public AddJavadocIntention() {
    super(PsiElement.class);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    FixDocCommentAction.generateComment(element, context.project(), updater);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    //noinspection DialogTitleCapitalization
    return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.LOW);
  }

  @Override
  protected boolean isElementApplicable(@NotNull PsiElement element, @NotNull ActionContext context) {
    if (element instanceof PsiIdentifier ||
        element instanceof PsiJavaCodeReferenceElement ||
        element instanceof PsiJavaModuleReferenceElement) {
      PsiElement targetElement = PsiTreeUtil.skipParentsOfType(element, PsiIdentifier.class, PsiJavaCodeReferenceElement.class, PsiJavaModuleReferenceElement.class);
      if (targetElement instanceof PsiVariable && PsiTreeUtil.isAncestor(((PsiVariable)targetElement).getInitializer(), element, false)) {
        return false;
      }
      if ( targetElement instanceof PsiClass aClass && PsiUtil.isLocalClass(aClass)) {
        return false;
      }
      if (targetElement instanceof PsiJavaDocumentedElement documentedElement &&
          !(targetElement instanceof PsiTypeParameter) &&
          !(targetElement instanceof PsiAnonymousClass)) {
        return documentedElement.getDocComment() == null;
      }

      if (targetElement instanceof PsiPackageStatement) {
        PsiFile file = targetElement.getContainingFile();
        return PackageUtil.isPackageInfoFile(file) && JavaDocumentationProvider.getPackageInfoComment(file) == null;
      }
      else if (PackageUtil.isPackageInfoFile(targetElement)) {
        return JavaDocumentationProvider.getPackageInfoComment(targetElement) == null;
      }
    }
    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    //noinspection DialogTitleCapitalization
    return JavaBundle.message("intention.family.add.javadoc");
  }
}
