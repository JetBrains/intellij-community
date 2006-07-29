package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class MissingIfBranchesFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiIfStatement)) return;

    PsiIfStatement ifStatement = (PsiIfStatement) psiElement;
    final Document doc = editor.getDocument();
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    final PsiKeyword elseElement = ifStatement.getElseElement();
    if (elseElement != null && (elseBranch == null || !(elseBranch instanceof PsiBlockStatement) &&
                                                      startLine(doc, elseBranch) > startLine(doc, elseElement))) {
      doc.insertString(elseElement.getTextRange().getEndOffset(), "{}");
    }

    PsiElement thenBranch = ifStatement.getThenBranch();
    if (thenBranch instanceof PsiBlockStatement) return;

    boolean transformingOneLiner = false;
    if (thenBranch != null && startLine(doc, thenBranch) == startLine(doc, ifStatement)) {
      if (ifStatement.getCondition() != null) {
        return;
      }
      transformingOneLiner = true;
    }

    final PsiJavaToken rParenth = ifStatement.getRParenth();
    assert rParenth != null;

    if (elseBranch == null && !transformingOneLiner || thenBranch == null) {
      doc.insertString(rParenth.getTextRange().getEndOffset(), "{}");
    }
    else {
      doc.insertString(rParenth.getTextRange().getEndOffset(), "{");
      doc.insertString(thenBranch.getTextRange().getEndOffset() + 1, "}");
    }
  }

  private static int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
