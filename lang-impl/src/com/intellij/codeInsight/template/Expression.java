package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

public interface Expression {
  Key AUTO_POPUP_NEXT_LOOKUP = Key.create("AUTO_POPUP_NEXT_LOOKUP");

  @Nullable
  Result calculateResult(ExpressionContext context);

  @Nullable
  Result calculateQuickResult(ExpressionContext context);

  @Nullable
  LookupItem[] calculateLookupItems(ExpressionContext context);
}

