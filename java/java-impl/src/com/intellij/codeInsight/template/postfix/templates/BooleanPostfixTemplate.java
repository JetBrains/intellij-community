package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import org.jetbrains.annotations.NotNull;

abstract public class BooleanPostfixTemplate extends PostfixTemplate {
  protected BooleanPostfixTemplate(@NotNull String name, @NotNull String description, @NotNull String example) {
    super(name, description, example);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression topmostExpression = getTopmostExpression(context);
    return topmostExpression != null &&
           topmostExpression.getParent() instanceof PsiExpressionStatement &&
           PostfixTemplatesUtils.isBoolean(topmostExpression.getType());
  }
}
