// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.IncorrectOperationException;

public class MissingCatchBodyFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiCatchSection catchSection)) return;

    final Document doc = editor.getDocument();

    PsiCodeBlock body = catchSection.getCatchBlock();
    if (body != null && body.getLBrace() != null && body.getRBrace() != null) return;

    final PsiJavaToken rParenth = catchSection.getRParenth();
    if (rParenth == null) return;

    doc.insertString(rParenth.getTextRange().getEndOffset(), "{}");
  }

}