package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.Nullable;

public abstract class Expression {

  @Nullable
  public abstract Result calculateResult(ExpressionContext context);

  @Nullable
  public abstract Result calculateQuickResult(ExpressionContext context);

  @Nullable
  public abstract LookupElement[] calculateLookupItems(ExpressionContext context);
}

