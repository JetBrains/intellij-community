package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class EnumMacro implements Macro{
  public String getName() {
    return "enum";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.enum");
  }

  public String getDefaultValue() {
    return "";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return null;
    return params[0].calculateResult(context);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return null;
    return params[0].calculateQuickResult(context);
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length ==0) return null;
    Set<LookupElement> set = new LinkedHashSet<LookupElement>();

    for (Expression param : params) {
      Result object = param.calculateResult(context);
      set.add(LookupElementBuilder.create(object.toString()));
    }
    return set.toArray(new LookupElement[set.size()]);
  }

}