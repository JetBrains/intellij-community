package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.HashSet;

import java.util.Iterator;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

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
    if (params == null || params.length == 0) return null;
    return params[0].calculateQuickResult(context);
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  public LookupItem[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    if (params == null || params.length == 0) return LookupItem.EMPTY_ARRAY;
    Result paramResult = params[0].calculateQuickResult(context);
    if (paramResult instanceof PsiTypeResult) {
      final PsiType type = ((PsiTypeResult)paramResult).getType();
      Set<PsiType> types = new HashSet<PsiType>();
      types.add(type);
      final PsiFile file = PsiDocumentManager.getInstance(context.getProject()).getPsiFile(context.getEditor().getDocument());
      final PsiElement element = file.findElementAt(context.getStartOffset());
      types.addAll(CodeInsightUtil.addSubtypes(type, element, false));
      LookupItem[] result = new LookupItem[types.size()];
      final Iterator<PsiType> it = types.iterator();
      for (int i = 0; i < result.length; i++) {
        result[i] = LookupItemUtil.objectToLookupItem(it.next());

      }
      return result;
    }
    return LookupItem.EMPTY_ARRAY;
  }
}