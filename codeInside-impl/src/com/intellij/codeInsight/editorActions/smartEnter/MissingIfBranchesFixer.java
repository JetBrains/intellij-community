package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingIfBranchesFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiIfStatement)) return;
    PsiIfStatement ifStatement = (PsiIfStatement) psiElement;
    final Document doc = editor.getDocument();
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (ifStatement.getElseElement() != null &&
        (elseBranch == null || !(elseBranch instanceof PsiBlockStatement) &&
                               startLine(doc, elseBranch) > startLine(doc, ifStatement.getElseElement()))) {
      doc.insertString(ifStatement.getElseElement().getTextRange().getEndOffset(), "{}");
    }

    PsiElement thenBranch = ifStatement.getThenBranch();
    if (thenBranch instanceof PsiBlockStatement) return;
    if (thenBranch != null && startLine(doc, thenBranch) == startLine(doc, ifStatement)) return;

    if (ifStatement.getElseBranch() == null || ifStatement.getThenBranch() == null) {
      doc.insertString(ifStatement.getRParenth().getTextRange().getEndOffset(), "{}");
    }
    else {
      doc.insertString(ifStatement.getRParenth().getTextRange().getEndOffset(), "{");
      doc.insertString(ifStatement.getThenBranch().getTextRange().getEndOffset() + 1, "}");
    }
  }

  private int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
