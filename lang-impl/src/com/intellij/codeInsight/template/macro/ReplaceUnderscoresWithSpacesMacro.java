package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman.Chernyatchik
 */
public class ReplaceUnderscoresWithSpacesMacro implements Macro {
  @NonNls
  public String getName() {
    return "underscoresToSpaces";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.undescoresToSpaces.string");
  }

  @NonNls
  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) {
      return null;
    }
    Result param_result = params[0].calculateResult(context);
    return execute(param_result);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    return LookupItem.EMPTY_ARRAY;
  }

  private Result execute(final Result param_result) {
    final String param_string_value = param_result.toString();
    return new TextResult(param_string_value.replace('_', ' '));
  }
}