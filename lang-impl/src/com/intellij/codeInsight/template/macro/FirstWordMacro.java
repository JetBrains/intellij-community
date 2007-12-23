package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.CodeInsightBundle;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class FirstWordMacro implements Macro {
  @NonNls
  public String getName() {
    return "firstWord";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.firstWord.string");
  }

  @NonNls
  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    Result result = params[0].calculateResult(context);
    return execute(result);
  }

  private Result execute(final Result result) {
    final String resultString = result.toString();
    final int index = resultString.indexOf(' ');
    return index >= 0 ? new TextResult(resultString.substring(0, index)) : result;
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    Result result = params[0].calculateResult(context);
    return execute(result);
  }

  public LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context) {
    return LookupItem.EMPTY_ARRAY;
  }
}
