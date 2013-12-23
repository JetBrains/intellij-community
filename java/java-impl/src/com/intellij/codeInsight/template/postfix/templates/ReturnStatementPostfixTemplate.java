package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class ReturnStatementPostfixTemplate extends PostfixTemplate {
  public ReturnStatementPostfixTemplate() {
    super("return", "Returns value from containing method", "return expr;");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expr = getTopmostExpression(context);
    if (expr == null || !(expr.getParent() instanceof PsiExpressionStatement)) return false;
    PsiType type = expr.getType();
    return type != null && !PsiType.VOID.equals(type);
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expr = getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    if (!(parent instanceof PsiExpressionStatement)) return;
    PsiElementFactory factory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
    PsiReturnStatement returnStatement = (PsiReturnStatement)factory.createStatementFromText("return " + expr.getText() + ";", parent);
    parent.replace(returnStatement);
  }
}
