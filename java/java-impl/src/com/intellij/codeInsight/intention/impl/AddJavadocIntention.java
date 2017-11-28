// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.editorActions.FixDocCommentAction;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.ide.util.PackageUtil;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AddJavadocIntention extends BaseElementAtCaretIntentionAction implements LowPriorityAction {
  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
    FixDocCommentAction.generateOrFixComment(element, project, editor);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull PsiElement element) {
    if (element instanceof PsiIdentifier ||
        element instanceof PsiJavaCodeReferenceElement ||
        element instanceof PsiJavaModuleReferenceElement) {
      PsiElement targetElement = PsiTreeUtil.skipParentsOfType(element, PsiIdentifier.class, PsiJavaCodeReferenceElement.class, PsiJavaModuleReferenceElement.class);
      if (targetElement instanceof PsiJavaDocumentedElement &&
          !(targetElement instanceof PsiTypeParameter) &&
          !(targetElement instanceof PsiAnonymousClass)) {
        return ((PsiJavaDocumentedElement)targetElement).getDocComment() == null;
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

  @NotNull
  @Override
  public String getFamilyName() {
    //noinspection DialogTitleCapitalization
    return "Add Javadoc";
  }

  @NotNull
  @Override
  public String getText() {
    //noinspection DialogTitleCapitalization
    return getFamilyName();
  }
}
