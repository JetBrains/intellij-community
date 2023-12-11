// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class JavaVcsSelectionProvider implements VcsSelectionProvider {
  @Override
  @Nullable
  public VcsSelection getSelection(@NotNull DataContext context) {
    final Editor editor = context.getData(CommonDataKeys.EDITOR);
    if (editor == null) return null;
    PsiElement psiElement = TargetElementUtil.findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    if (psiElement == null) {
      return null;
    }
    if (!psiElement.isValid()) {
      return null;
    }
    if (psiElement instanceof PsiCompiledElement) {
      return null;
    }

    final String actionName;

    if (psiElement instanceof PsiClass) {
      actionName = VcsBundle.message("action.name.show.history.for.class");
    }
    else if (psiElement instanceof PsiField) {
      actionName = VcsBundle.message("action.name.show.history.for.field");
    }
    else if (psiElement instanceof PsiMethod) {
      actionName = VcsBundle.message("action.name.show.history.for.method");
    }
    else if (psiElement instanceof PsiCodeBlock) {
      actionName = VcsBundle.message("action.name.show.history.for.code.block");
    }
    else if (psiElement instanceof PsiStatement) {
      actionName = VcsBundle.message("action.name.show.history.for.statement");
    }
    else {
      return null;
    }

    TextRange textRange = psiElement.getTextRange();
    if (textRange == null) {
      return null;
    }

    VirtualFile virtualFile = psiElement.getContainingFile().getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    if (!virtualFile.isValid()) {
      return null;
    }

    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) {
      return null;
    }

    return new VcsSelection(document, textRange, actionName);
  }
}
