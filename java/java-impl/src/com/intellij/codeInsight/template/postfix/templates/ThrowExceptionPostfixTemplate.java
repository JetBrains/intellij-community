package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import org.jetbrains.annotations.NotNull;

public class ThrowExceptionPostfixTemplate extends PostfixTemplate {
  public ThrowExceptionPostfixTemplate() {
    super("throw", "Throws expression of 'Throwable' type", "throw expr;");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expr = getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    return parent instanceof PsiExpressionStatement;
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PostfixTemplatesUtils.createSimpleStatement(context, editor, "throw");
  }
}