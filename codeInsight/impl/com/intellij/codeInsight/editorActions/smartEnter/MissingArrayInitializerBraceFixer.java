package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingArrayInitializerBraceFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiArrayInitializerExpression)) return;
    PsiArrayInitializerExpression expr = (PsiArrayInitializerExpression)psiElement;
    final Document doc = editor.getDocument();
    final String exprText = expr.getText();
    final TextRange textRange = expr.getTextRange();
    final int endOffset = textRange.getEndOffset();
    int caretOffset = editor.getCaretModel().getOffset();
    final int startOffset = textRange.getStartOffset();
    if (caretOffset > startOffset && caretOffset < endOffset) {
      final int index = exprText.indexOf('\n', caretOffset - startOffset);
      if (index >= 0) {
        doc.insertString(index + startOffset, "}");
        return;
      }
    }
    if (!exprText.endsWith("}")) {
      doc.insertString(endOffset, "}");
    }
  }
}