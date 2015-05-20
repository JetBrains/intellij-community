/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.*;

public abstract class ForIndexedPostfixTemplate extends StringBasedPostfixTemplate {

  public static final Condition<PsiElement> IS_NUMBER_OR_ARRAY_OR_ITERABLE = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement element) {
      return IS_ITERABLE_OR_ARRAY.value(element) || IS_NUMBER.value(element);
    }
  };

  protected ForIndexedPostfixTemplate(@NotNull String key, @NotNull String example) {
    super(key, example, selectorTopmost(IS_NUMBER_OR_ARRAY_OR_ITERABLE));
  }

  @Override
  public void setVariables(@NotNull Template template, @NotNull PsiElement element) {
    MacroCallNode index = new MacroCallNode(new SuggestVariableNameMacro());
    template.addVariable("index", index, index, true);
  }

  @Override
  public final String getTemplateString(@NotNull PsiElement element) {
    PsiExpression expr = (PsiExpression)element;
    String bound = getExpressionBound(expr);
    if (bound == null) {
      return null;
    }

    return getStringTemplate(expr).replace("$bound$", bound).replace("$type$", suggestIndexType(expr));
  }

  @NotNull
  protected abstract String getStringTemplate(@NotNull PsiExpression expr);

  @Nullable
  private static String getExpressionBound(@NotNull PsiExpression expr) {
    PsiType type = expr.getType();
    if (isNumber(type)) {
      return expr.getText();
    }
    else if (isArray(type)) {
      return expr.getText() + ".length";
    }
    else if (isIterable(type)) {
      return expr.getText() + ".size()";
    }
    return null;
  }

  @NotNull
  private static String suggestIndexType(@NotNull PsiExpression expr) {
    PsiType type = expr.getType();
    if (isNumber(type)) {
      return type.getCanonicalText();
    }
    return "int";
  }

  @Override
  protected boolean shouldAddExpressionToContext() {
    return false;
  }
}