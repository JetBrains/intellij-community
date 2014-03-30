package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ForDescendingPostfixTemplate extends ForIndexedPostfixTemplate {
  public ForDescendingPostfixTemplate() {
    super("forr", "Iterates with index in reverse order", "for (int i = expr.length-1; i >= 0; i--)");
  }

  @Override
  @NotNull
  protected String getOperator() {
    return "--";
  }

  @NotNull
  @Override
  protected String getComparativeSign(@NotNull PsiExpression expr) {
    return PostfixTemplatesUtils.isNumber(expr.getType()) ? ">" : ">=";
  }

  @Nullable
  @Override
  protected Pair<String, String> calculateBounds(@NotNull PsiExpression expression) {
    String bound = getExpressionBound(expression);
    if (bound == null) {
      return null;
    }
    return PostfixTemplatesUtils.isNumber(expression.getType())
      ? Pair.create(bound, "0")
      : Pair.create(bound + " - 1", "0");
  }
}