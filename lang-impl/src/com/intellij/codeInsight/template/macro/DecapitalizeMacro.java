package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import org.jetbrains.annotations.NotNull;

public class DecapitalizeMacro implements Macro {

  public String getName() {
    return "decapitalize";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.decapitalize.string");
  }

  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    Result result = params[0].calculateResult(context);
    return execute(result);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    Result result = params[0].calculateQuickResult(context);
    return execute(result);
  }

  private Result execute(Result result) {
    if (result == null) return null;
    String text = result.toString();
    if (text.length() > 0) {
      text = text.substring(0, 1).toLowerCase() + text.substring(1, text.length());
    }
    return new TextResult(text);
  }

  public LookupItem[] calculateLookupItems(@NotNull Expression[] params, final ExpressionContext context) {
    return null;
  }
}
