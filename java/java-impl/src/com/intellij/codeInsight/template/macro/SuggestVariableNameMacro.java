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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedList;

public class SuggestVariableNameMacro extends Macro {

  @Override
  public String getName() {
    return "suggestVariableName";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.suggest.variable.name");
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    String[] names = getNames(context);
    if (names == null || names.length == 0) return null;
    return new TextResult(names[0]);
  }

  @Nullable
  @Override
  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, final ExpressionContext context) {
    String[] names = getNames(context);
    if (names == null || names.length < 2) return null;
    LookupElement[] items = new LookupElement[names.length];
    for(int i = 0; i < names.length; i++) {
      items[i] = LookupElementBuilder.create(names[i]);
    }
    return items;
  }

  private static String[] getNames (final ExpressionContext context) {
    String[] names = ExpressionUtil.getNames(context);
    if (names == null || names.length == 0) return names;
    PsiFile file = PsiDocumentManager.getInstance(context.getProject()).getPsiFile(context.getEditor().getDocument());
    PsiElement e = file.findElementAt(context.getStartOffset());
    PsiVariable[] vars = MacroUtil.getVariablesVisibleAt(e, "");
    LinkedList<String> namesList = new LinkedList<>(Arrays.asList(names));
    for (PsiVariable var : vars) {
      if (e.equals(var.getNameIdentifier())) continue;
      namesList.remove(var.getName());
    }

    if (namesList.isEmpty()) {
      String name = names[0];
      index:
      for (int j = 1; ; j++) {
        String name1 = name + j;
        for (PsiVariable var : vars) {
          if (name1.equals(var.getName()) && !var.getNameIdentifier().equals(e)) continue index;
        }
        return new String[]{name1};
      }
    }

    return ArrayUtil.toStringArray(namesList);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }


}
