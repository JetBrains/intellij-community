package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ForAscendingPostfixTemplate extends ForIndexedPostfixTemplate {
  public ForAscendingPostfixTemplate() {
    super("fori", "Iterates with index over collection", "for (int i = 0; i < expr.length; i++)");
  }

  @Override
  @NotNull
  protected String getOperator() {
    return "++";
  }

  @NotNull
  @Override
  protected String getComparativeSign(@NotNull PsiExpression expr) {
    return "<";
  }

  @Nullable
  @Override
  protected Pair<String, String> calculateBounds(@NotNull PsiExpression expression) {
    String bound = getExpressionBound(expression);
    return bound != null ? Pair.create("0", bound) : null;
  }
}