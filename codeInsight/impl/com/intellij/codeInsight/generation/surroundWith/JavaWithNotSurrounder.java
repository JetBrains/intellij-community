
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

class JavaWithNotSurrounder extends JavaExpressionSurrounder{
  public boolean isApplicable(PsiExpression expr) {
    return PsiType.BOOLEAN == expr.getType();
  }

  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    PsiManager manager = expr.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    PsiPrefixExpression prefixExpr = (PsiPrefixExpression)factory.createExpressionFromText("!(a)", null);
    prefixExpr = (PsiPrefixExpression)codeStyleManager.reformat(prefixExpr);
    ((PsiParenthesizedExpression)prefixExpr.getOperand()).getExpression().replace(expr);
    expr = (PsiExpression)expr.replace(prefixExpr);
    int offset = expr.getTextRange().getEndOffset();
    return new TextRange(offset, offset);
  }

  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.not.template");
  }
}