// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;

public class BlockBraceFixer implements Fixer{
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiCodeBlock block && afterUnmatchedBrace(editor, psiElement.getContainingFile().getFileType())) {
      int stopOffset = block.getTextRange().getEndOffset();
      final PsiStatement[] statements = block.getStatements();
      if (statements.length > 0) {
        stopOffset = statements[0].getTextRange().getEndOffset();
      }
      editor.getDocument().insertString(stopOffset, "}");
    }
  }

  static boolean afterUnmatchedBrace(Editor editor, FileType fileType) {
    return EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, editor.getCaretModel().getOffset(), fileType);
  }
}
