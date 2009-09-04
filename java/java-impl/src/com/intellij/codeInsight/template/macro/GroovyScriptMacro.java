package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.util.text.StringUtil;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Maxim.Mossienko
 */
public class GroovyScriptMacro implements Macro {
  public String getName() {
    return "groovyScript";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.groovy.script");
  }

  public String getDefaultValue() {
    return "";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return null;
    Object o = runIt(params, context);
    if (o != null) return new TextResult(o.toString());
    return null;
  }

  private static Object runIt(Expression[] params, ExpressionContext context) {
    try {
      Result result = params[0].calculateResult(context);
      if (result == null) return result;
      String text = result.toString();
      GroovyShell shell = new GroovyShell();
      File possibleFile = new File(text);
      Script script = possibleFile.exists() ? shell.parse(possibleFile) :  shell.parse(text);
      Binding binding = new Binding();

      for(int i = 1; i < params.length; ++i) {
        Result paramResult = params[i].calculateResult(context);
        binding.setVariable("_"+i, paramResult != null ? paramResult.toString():null);
      }

      binding.setVariable("_editor", context.getEditor());

      script.setBinding(binding);

      Object o = script.run();
      return o != null ? StringUtil.convertLineSeparators(o.toString()):null;
    } catch (Exception e) {
      return new TextResult(StringUtil.convertLineSeparators(e.getLocalizedMessage()));
    }
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    Object o = runIt(params, context);
    if (o != null) {
      Set<LookupElement> set = new LinkedHashSet<LookupElement>();
      set.add(LookupElementBuilder.create(o.toString()));
      return set.toArray(new LookupElement[set.size()]);
    }
    return LookupElement.EMPTY_ARRAY;
  }
}
