// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.util.IncorrectOperationException;

@SuppressWarnings({"HardCodedStringLiteral"})
public class MissingThrowExpressionFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement)
      throws IncorrectOperationException {
    if (psiElement instanceof PsiThrowStatement throwStatement) {
      if (throwStatement.getException() != null &&
          startLine(editor, throwStatement) == startLine(editor, throwStatement.getException())) {
        return;
      }

      final int startOffset = throwStatement.getTextRange().getStartOffset();
      if (throwStatement.getException() != null) {
        editor.getDocument().insertString(startOffset + "throw".length(), ";");
      }
      processor.registerUnresolvedError(startOffset + "throw".length());
    }
  }

  private static int startLine(Editor editor, PsiElement psiElement) {
    return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
