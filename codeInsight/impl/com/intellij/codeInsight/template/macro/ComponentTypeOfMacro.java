package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;

public class ComponentTypeOfMacro implements Macro {
  public String getName() {
    return "componentTypeOf";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.component.type.of.array");
  }

  public String getDefaultValue() {
    return "A";
  }

  public LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    LookupItem[] lookupItems = params[0].calculateLookupItems(context);
    if (lookupItems == null) return null;

    for (LookupItem item : lookupItems) {
      Integer bracketsCount = (Integer)item.getAttribute(LookupItem.BRACKETS_COUNT_ATTR);
      if (bracketsCount == null) return null;
      item.setAttribute(LookupItem.BRACKETS_COUNT_ATTR, new Integer(bracketsCount.intValue() - 1));
    }

    return lookupItems;
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public Result calculateResult(Expression[] params, final ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    if (result instanceof PsiTypeResult) {
      PsiType type = ((PsiTypeResult) result).getType();
      if (type instanceof PsiArrayType) {
        return new PsiTypeResult(((PsiArrayType) type).getComponentType(), context.getProject());
      }
    }

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    PsiType type;
    if (expr == null) {
      type = MacroUtil.resultToPsiType(result, context);
    }
    else{
      type = expr.getType();
    }
    if (type instanceof PsiArrayType) {
      return new PsiTypeResult(((PsiArrayType) type).getComponentType(), context.getProject());
    }

    return new PsiElementResult(null);
  }
}

