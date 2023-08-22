// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.IncorrectOperationException;

@SuppressWarnings({"HardCodedStringLiteral"})
public class CatchDeclarationFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiCatchSection catchSection) {
      final Document doc = editor.getDocument();

      final int catchStart = catchSection.getTextRange().getStartOffset();
      int stopOffset = doc.getLineEndOffset(doc.getLineNumber(catchStart));

      final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
      if (catchBlock != null) {
        stopOffset = Math.min(stopOffset, catchBlock.getTextRange().getStartOffset());
      }
      stopOffset = Math.min(stopOffset, catchSection.getTextRange().getEndOffset());

      final PsiJavaToken lParenth = catchSection.getLParenth();
      if (lParenth == null) {
        doc.replaceString(catchStart, stopOffset, "catch ()");
        processor.registerUnresolvedError(catchStart + "catch (".length());
      } else {
        if (catchSection.getParameter() == null) {
          processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
        }
        if (catchSection.getRParenth() == null) {
          doc.insertString(stopOffset, ")");
        }
      }
    }
  }
}