package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.lang.LangBundle;
import org.jetbrains.annotations.NotNull;

public class MethodNameMacro implements Macro {

  public String getName() {
    return "methodName";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.methodname");
  }

  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@NotNull Expression[] params, final ExpressionContext context) {
    Project project = context.getProject();
    int templateStartOffset = context.getTemplateStartOffset();
    final int offset = templateStartOffset > 0 ? context.getTemplateStartOffset() - 1 : context.getTemplateStartOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    while(place != null){
      if (place instanceof PsiMethod){
        return new TextResult(((PsiMethod)place).getName());
      } else if (place instanceof PsiClassInitializer) {
        return ((PsiClassInitializer) place).hasModifierProperty(PsiModifier.STATIC) ?
               new TextResult(LangBundle.message("java.terms.static.initializer")) :
               new TextResult(LangBundle.message("java.terms.instance.initializer"));
      }
      place = place.getParent();
    }
    return null;
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, final ExpressionContext context) {
    return null;
  }
}
