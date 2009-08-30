package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingCatchBodyFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiCatchSection)) return;
    PsiCatchSection catchSection = (PsiCatchSection) psiElement;

    final Document doc = editor.getDocument();

    PsiCodeBlock body = catchSection.getCatchBlock();
    if (body != null && startLine(doc, body) == startLine(doc, catchSection)) return;

    final PsiJavaToken rParenth = catchSection.getRParenth();
    if (rParenth == null) return;

    doc.insertString(rParenth.getTextRange().getEndOffset(), "{}");
  }

  private static int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}