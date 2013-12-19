package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.JavaSurroundersProxy;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public class CastExpressionPostfixTemplate extends ExpressionPostfixTemplateWithChooser {
  public CastExpressionPostfixTemplate() {
    super("cast", "Surrounds expression with cast", "((SomeType) expr)");
  }

  @Override
  protected void doIt(@NotNull final Editor editor, @NotNull final PsiExpression expression) {
    JavaSurroundersProxy.cast(expression.getProject(), editor, expression);
  }
}