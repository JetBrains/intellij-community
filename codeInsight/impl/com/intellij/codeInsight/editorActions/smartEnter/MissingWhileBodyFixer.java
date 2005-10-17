package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingWhileBodyFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiWhileStatement)) return;
    PsiWhileStatement whileStatement = (PsiWhileStatement) psiElement;

    final Document doc = editor.getDocument();

    PsiElement body = whileStatement.getBody();
    if (body instanceof PsiBlockStatement) return;
    if (body != null && startLine(doc, body) == startLine(doc, whileStatement)) return;

    doc.insertString(whileStatement.getRParenth().getTextRange().getEndOffset(), "{}");
  }

  private int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
