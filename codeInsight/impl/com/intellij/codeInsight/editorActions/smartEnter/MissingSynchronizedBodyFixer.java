package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingSynchronizedBodyFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiSynchronizedStatement)) return;
    PsiSynchronizedStatement syncStatement = (PsiSynchronizedStatement) psiElement;

    final Document doc = editor.getDocument();

    PsiElement body = syncStatement.getBody();
    if (body != null) return;

    doc.insertString(syncStatement.getTextRange().getEndOffset(), "{}");
  }
}
