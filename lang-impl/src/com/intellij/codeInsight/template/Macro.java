package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupItem;
import org.jetbrains.annotations.NonNls;

public interface Macro {
  @NonNls String getName();

  String getDescription ();

  @NonNls String getDefaultValue();

  Result calculateResult(Expression[] params, ExpressionContext context);

  Result calculateQuickResult(Expression[] params, ExpressionContext context);

  LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context);
}
