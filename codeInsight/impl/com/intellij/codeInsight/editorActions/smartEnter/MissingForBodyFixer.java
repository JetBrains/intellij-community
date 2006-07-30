package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  private static PsiForStatement getForStatementParent(PsiElement psiElement) {
    PsiForStatement statement = PsiTreeUtil.getParentOfType(psiElement, PsiForStatement.class);
    if (statement == null) return null;

    PsiStatement init = statement.getInitialization();
    PsiStatement update = statement.getUpdate();
    PsiExpression check = statement.getCondition();

    return isValidChild(init, psiElement) || isValidChild(update, psiElement) || isValidChild(check, psiElement) ? statement : null;
  }

  private static boolean isValidChild(PsiElement ancestor, PsiElement psiElement) {
    if (ancestor != null) {
      if (PsiTreeUtil.isAncestor(ancestor, psiElement, false)) {
        if (PsiTreeUtil.hasErrorElements(ancestor)) return false;
        return true;
      }
    }

    return false;
  }

  private static int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
