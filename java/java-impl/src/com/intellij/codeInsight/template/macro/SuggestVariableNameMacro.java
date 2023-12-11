// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedList;

public final class SuggestVariableNameMacro extends Macro {

  @Override
  public String getName() {
    return "suggestVariableName";
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    String[] names = getNames(context);
    if (names == null || names.length == 0) return null;
    return new TextResult(names[0]);
  }

  @Nullable
  @Override
  public Result calculateQuickResult(Expression @NotNull [] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public LookupElement[] calculateLookupItems(Expression @NotNull [] params, final ExpressionContext context) {
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

    return ArrayUtilRt.toStringArray(namesList);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

  @NotNull
  @Override
  public LookupFocusDegree getLookupFocusDegree() {
    return LookupFocusDegree.UNFOCUSED;
  }
}
