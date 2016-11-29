/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
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
public class GroovyScriptMacro extends Macro {
  @Override
  public String getName() {
    return "groovyScript";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.groovy.script");
  }

  @Override
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
        Object value = null;
        if (paramResult instanceof ListResult) {
          value = ContainerUtil.map2List(((ListResult)paramResult).getComponents(), result1 -> result1.toString());
        } else if (paramResult != null) {
          value = paramResult.toString();
        }
        binding.setVariable("_"+i, value);
      }

      binding.setVariable("_editor", context.getEditor());

      script.setBinding(binding);

      Object o = script.run();
      return o != null ? StringUtil.convertLineSeparators(o.toString()):null;
    } catch (Exception e) {
      return new TextResult(StringUtil.convertLineSeparators(e.getLocalizedMessage()));
    }
  }

  @Override
  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    Object o = runIt(params, context);
    if (o != null) {
      Set<LookupElement> set = new LinkedHashSet<>();
      set.add(LookupElementBuilder.create(o.toString()));
      return set.toArray(new LookupElement[set.size()]);
    }
    return LookupElement.EMPTY_ARRAY;
  }
}
