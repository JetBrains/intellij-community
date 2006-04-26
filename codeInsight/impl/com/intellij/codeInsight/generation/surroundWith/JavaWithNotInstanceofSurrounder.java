
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

class JavaWithNotInstanceofSurrounder extends JavaExpressionSurrounder{
  public boolean isApplicable(PsiExpression expr) {
    PsiType type = expr.getType();
    if (type == null) return false;
    return !(type instanceof PsiPrimitiveType);
  }

  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    PsiManager manager = expr.getManager();
    PsiElementFactory factory = manager.getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    PsiPrefixExpression prefixExpr = (PsiPrefixExpression)factory.createExpressionFromText("!(a instanceof Type)", null);
    prefixExpr = (PsiPrefixExpression)codeStyleManager.reformat(prefixExpr);
    PsiParenthesizedExpression parenthExpr = (PsiParenthesizedExpression)prefixExpr.getOperand();
    PsiInstanceOfExpression instanceofExpr = (PsiInstanceOfExpression)parenthExpr.getExpression();
    instanceofExpr.getOperand().replace(expr);
    prefixExpr = (PsiPrefixExpression)expr.replace(prefixExpr);
    parenthExpr = (PsiParenthesizedExpression)prefixExpr.getOperand();
    instanceofExpr = (PsiInstanceOfExpression)parenthExpr.getExpression();
    instanceofExpr = CodeInsightUtil.forcePsiPosprocessAndRestoreElement(instanceofExpr);
    TextRange range = instanceofExpr.getCheckType().getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    return new TextRange(range.getStartOffset(), range.getStartOffset());
  }

  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.not.instanceof.template");
  }
}