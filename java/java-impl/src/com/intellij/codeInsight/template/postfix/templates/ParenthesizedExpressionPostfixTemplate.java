package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import org.jetbrains.annotations.NotNull;

public class ParenthesizedExpressionPostfixTemplate extends ExpressionPostfixTemplateWithChooser {
  public ParenthesizedExpressionPostfixTemplate() {
    super("par", "Parenthesizes expression", "(expression)");
  }

  @Override
  protected void doIt(@NotNull Editor editor, @NotNull PsiExpression expression) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
    PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)factory.createExpressionFromText("(expr)", expression.getParent());
    PsiExpression operand = parenthesizedExpression.getExpression();
    assert operand != null;
    operand.replace(expression);
    expression.replace(parenthesizedExpression);
  }
}