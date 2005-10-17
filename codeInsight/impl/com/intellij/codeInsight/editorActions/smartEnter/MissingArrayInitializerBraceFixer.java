package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
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
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiArrayInitializerExpression)) return;
    PsiArrayInitializerExpression expr = (PsiArrayInitializerExpression)psiElement;

    final Document doc = editor.getDocument();

    if (!expr.getText().endsWith("}")) {
      doc.insertString(expr.getTextRange().getEndOffset(), "}");
    }
  }
}
