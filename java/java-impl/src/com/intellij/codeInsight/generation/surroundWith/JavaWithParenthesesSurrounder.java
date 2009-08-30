
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

class JavaWithParenthesesSurrounder extends JavaExpressionSurrounder{
  public boolean isApplicable(PsiExpression expr) {
    return true;
  }

  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    PsiManager manager = expr.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    PsiParenthesizedExpression parenthExpr = (PsiParenthesizedExpression)factory.createExpressionFromText("(a)", null);
    parenthExpr = (PsiParenthesizedExpression)codeStyleManager.reformat(parenthExpr);
    parenthExpr.getExpression().replace(expr);
    expr = (PsiExpression)expr.replace(parenthExpr);
    int offset = expr.getTextRange().getEndOffset();
    return new TextRange(offset, offset);
  }

  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.parenthesis.template");
  }
}