package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

public interface Expression {

  @Nullable
  Result calculateResult(ExpressionContext context);

  @Nullable
  Result calculateQuickResult(ExpressionContext context);

  @Nullable
  LookupItem[] calculateLookupItems(ExpressionContext context);
}

