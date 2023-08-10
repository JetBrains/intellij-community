// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

@SuppressWarnings({"HardCodedStringLiteral"})
public class DoWhileConditionFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiDoWhileStatement stmt) {
      final Document doc = editor.getDocument();
      if (stmt.getBody() == null || !(stmt.getBody() instanceof PsiBlockStatement) && stmt.getWhileKeyword() == null) {
        final int startOffset = stmt.getTextRange().getStartOffset();
        doc.replaceString(startOffset, startOffset + "do".length(), "do {} while()");
        return;
      }

      if (stmt.getCondition() == null) {
        if (stmt.getWhileKeyword() == null) {
          final int endOffset = stmt.getTextRange().getEndOffset();
          doc.insertString(endOffset, "while()");
        } else if (stmt.getLParenth() == null || stmt.getRParenth() == null) {
          final TextRange whileRange = stmt.getWhileKeyword().getTextRange();
          doc.replaceString(whileRange.getStartOffset(), whileRange.getEndOffset(), "while()");
        } else {
          processor.registerUnresolvedError(stmt.getLParenth().getTextRange().getEndOffset());
        }
      }
    }
  }
}
