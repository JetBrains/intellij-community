package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.codeInsight.template.postfix.util.CommonUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public class NotExpressionPostfixTemplate extends ExpressionPostfixTemplateWithChooser {
  public NotExpressionPostfixTemplate() {
    super("not", "Negates boolean expression", "!expr");
  }

  @Override
  protected void doIt(@NotNull Editor editor, @NotNull PsiExpression expression) {
    expression.replace(CodeInsightServicesUtil.invertCondition(expression));
  }

  @NotNull
  @Override
  protected Condition<PsiExpression> getTypeCondition() {
    return new Condition<PsiExpression>() {
      @Override
      public boolean value(PsiExpression expression) {
        return CommonUtils.isBoolean(expression.getType());
      }
    };
  }
}