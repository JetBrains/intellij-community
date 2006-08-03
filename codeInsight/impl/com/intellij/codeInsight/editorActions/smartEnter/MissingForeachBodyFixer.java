package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 5, 2004
 * Time: 8:44:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class MissingForeachBodyFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    PsiForeachStatement forStatement = getForeachStatementParent(psiElement);
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

  private static PsiForeachStatement getForeachStatementParent(PsiElement psiElement) {
    PsiForeachStatement statement = PsiTreeUtil.getParentOfType(psiElement, PsiForeachStatement.class);
    if (statement == null) return null;

    PsiExpression iterated = statement.getIteratedValue();
    PsiParameter parameter = statement.getIterationParameter();

    return PsiTreeUtil.isAncestor(iterated, psiElement, false) || PsiTreeUtil.isAncestor(parameter, psiElement, false) ? statement : null;
  }

  private static int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
