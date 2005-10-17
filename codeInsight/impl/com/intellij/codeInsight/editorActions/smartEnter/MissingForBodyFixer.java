package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingForBodyFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    PsiForStatement forStatement = getForStatementParent(psiElement);
    if (forStatement == null) return;

    final Document doc = editor.getDocument();

    PsiElement body = forStatement.getBody();
    if (body instanceof PsiBlockStatement) return;
    if (body != null && startLine(doc, body) == startLine(doc, forStatement)) return;

    PsiElement eltToInsertAfter = forStatement.getRParenth();
    String text = "{}";
    if (eltToInsertAfter == null) {
      eltToInsertAfter = forStatement;
      text = "){}";
    }
    doc.insertString(eltToInsertAfter.getTextRange().getEndOffset(), text);
  }

  private PsiForStatement getForStatementParent(PsiElement psiElement) {
    PsiForStatement statement = PsiTreeUtil.getParentOfType(psiElement, PsiForStatement.class);
    if (statement == null) return null;

    PsiStatement init = statement.getInitialization();
    PsiStatement update = statement.getUpdate();
    PsiExpression check = statement.getCondition();

    return isAncestor(init, psiElement) || isAncestor(update, psiElement) || isAncestor(check, psiElement) ? statement : null;
  }

  private boolean isAncestor(PsiElement ancestor, PsiElement psiElement) {
    return ancestor != null && PsiTreeUtil.isAncestor(ancestor, psiElement, false);
  }

  private int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
