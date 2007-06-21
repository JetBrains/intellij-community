package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;

public class TypeOfVariableMacro implements Macro {
  public String getName() {
    return "typeOfVariable";
  }

  public String getDescription() {
    return "typeOfVariable(VAR)";
  }

  public String getDefaultValue() {
    return "A";
  }

  public Result calculateResult(Expression[] params, ExpressionContext context) {
    if (params == null || params.length == 0) return null;

    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    Result result = params[0].calculateQuickResult(context);
    if (result instanceof PsiElementResult) {
      final PsiElement element = ((PsiElementResult)result).getElement();
      if (element instanceof PsiVariable) {
        return new PsiTypeResult(((PsiVariable)element).getType(), element.getManager());
      }
    } else if (result instanceof TextResult) {
      PsiFile file = PsiDocumentManager.getInstance(context.getProject()).getPsiFile(context.getEditor().getDocument());
      PsiElement place = file.findElementAt(context.getStartOffset());
      final PsiVariable[] vars = MacroUtil.getVariablesVisibleAt(place, "");
      final String name = result.toString();
      for (final PsiVariable var : vars) {
        if (name.equals(var.getName())) return new PsiTypeResult(var.getType(), var.getManager());
      }
    }
    return null;
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  public LookupItem[] calculateLookupItems(Expression[] params, ExpressionContext context) {
    return LookupItem.EMPTY_ARRAY;
  }

}