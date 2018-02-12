/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcsUtil;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaVcsSelectionProvider implements VcsSelectionProvider {
  @Nullable
  public VcsSelection getSelection(final VcsContext context) {
    final Editor editor = context.getEditor();
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
