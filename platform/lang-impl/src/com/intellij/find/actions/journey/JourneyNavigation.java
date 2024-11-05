// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions.journey;

import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public final class JourneyNavigation {

  public static PsiElement editorToPsiElement(Project project, Editor editor) {
    // Get current caret offset
    CaretModel caretModel = editor.getCaretModel();
    int caretOffset = caretModel.getOffset();

    // Get the document corresponding to the editor
    Document document = editor.getDocument();

    // Get the PsiFile corresponding to the document
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);

    // Get the PSI element at the caret offset

    // Ascend the tree to find the enclosing PsiMethod
    return psiFile != null ? psiFile.findElementAt(caretOffset) : null;
  }

}
