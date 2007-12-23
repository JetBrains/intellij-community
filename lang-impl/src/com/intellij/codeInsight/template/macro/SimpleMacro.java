package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class SimpleMacro implements Macro {
  private String myName;

  protected SimpleMacro(final String name) {
    myName = name;
  }

  @NonNls
  public String getName() {
    return myName;
  }

  public String getDescription() {
    return myName + "()";
  }

  @NonNls
  public String getDefaultValue() {
    return "11.11.1111";
  }

  public Result calculateResult(final Expression[] params, final ExpressionContext context) {
    return new TextResult(evaluate());
  }

  public Result calculateQuickResult(final Expression[] params, final ExpressionContext context) {
    return calculateResult(params, context);
  }

  public LookupItem[] calculateLookupItems(final Expression[] params, final ExpressionContext context) {
    return LookupItem.EMPTY_ARRAY;
  }

  protected abstract String evaluate();
}