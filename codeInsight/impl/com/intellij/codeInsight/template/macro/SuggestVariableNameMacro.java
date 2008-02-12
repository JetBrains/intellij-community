package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;

import java.util.Arrays;
import java.util.LinkedList;

import org.jetbrains.annotations.NotNull;

public class SuggestVariableNameMacro implements Macro {

  public String getName() {
    return "suggestVariableName";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.suggest.variable.name");
  }

  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    String[] names = getNames(context);
    if (names == null || names.length == 0) return null;
    return new TextResult(names[0]);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(@NotNull Expression[] params, final ExpressionContext context) {
    String[] names = getNames(context);
    if (names == null || names.length < 2) return null;
    LookupItem[] items = new LookupItem[names.length];
    for(int i = 0; i < names.length; i++) {
      String name = names[i];
      items[i] = new LookupItem(name, name);
    }
    return items;
  }

  private String[] getNames (final ExpressionContext context) {
    String[] names = ExpressionUtil.getNames(context);
    if (names == null || names.length == 0) return names;
    PsiFile file = PsiDocumentManager.getInstance(context.getProject()).getPsiFile(context.getEditor().getDocument());
    PsiElement e = file.findElementAt(context.getStartOffset());
    PsiVariable[] vars = MacroUtil.getVariablesVisibleAt(e, "");
    LinkedList namesList = new LinkedList(Arrays.asList(names));
    for (PsiVariable var : vars) {
      if (e.equals(var.getNameIdentifier())) continue;
      namesList.remove(var.getName());
    }

    if (namesList.size() == 0) {
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

    return (String[]) namesList.toArray(new String[namesList.size()]);
  }

}
