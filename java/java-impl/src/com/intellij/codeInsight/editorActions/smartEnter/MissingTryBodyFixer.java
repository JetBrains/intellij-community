// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.util.IncorrectOperationException;

public class MissingTryBodyFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiTryStatement tryStatement)) return;

    final Document doc = editor.getDocument();

    PsiElement body = tryStatement.getTryBlock();
    if (body != null) return;

    doc.insertString(tryStatement.getTextRange().getEndOffset(), "{}");
  }

}
