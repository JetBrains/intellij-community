package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class SubtypesMacro implements Macro {
  public String getName() {
    return "subtypes";
  }

  public String getDescription() {
    return "subtypes(TYPE)";
  }

  public String getDefaultValue() {
    return "A";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return null;
    return params[0].calculateQuickResult(context);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return LookupItem.EMPTY_ARRAY;
    Result paramResult = params[0].calculateQuickResult(context);
    if (paramResult instanceof PsiTypeResult) {
      final PsiType type = ((PsiTypeResult)paramResult).getType();
      Set<PsiType> types = new HashSet<PsiType>();
      types.add(type);
      final PsiFile file = PsiDocumentManager.getInstance(context.getProject()).getPsiFile(context.getEditor().getDocument());
      final PsiElement element = file.findElementAt(context.getStartOffset());
      types.addAll(CodeInsightUtil.addSubtypes(type, element, false));
      final Set<LookupItem> set = new LinkedHashSet<LookupItem>();
      for (PsiType t: types) {
        JavaTemplateUtil.addTypeLookupItem(set, t);
      }
      return set.toArray(new LookupItem[set.size()]);
    }
    return LookupItem.EMPTY_ARRAY;
  }
}