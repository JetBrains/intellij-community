package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 8, 2003
 * Time: 4:20:36 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class MissingThrowExpressionFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement)
      throws IncorrectOperationException {
    if (psiElement instanceof PsiThrowStatement) {
      PsiThrowStatement throwStatement = (PsiThrowStatement) psiElement;
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

  private int startLine(Editor editor, PsiElement psiElement) {
    return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
