package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class MethodReturnTypeMacro implements Macro {
  public String getName() {
    return "methodReturnType";
  }

  public String getDescription() {
    return "methodReturnType()";
  }

  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@NotNull final Expression[] params, final ExpressionContext context) {
    Project project = context.getProject();
    int templateStartOffset = context.getTemplateStartOffset();
    final int offset = templateStartOffset > 0 ? context.getTemplateStartOffset() - 1 : context.getTemplateStartOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    while(place != null){
      if (place instanceof PsiMethod){
        return new PsiTypeResult(((PsiMethod)place).getReturnType(), place.getProject());
      }
      place = place.getParent();
    }
    return null;
  }

  public Result calculateQuickResult(@NotNull final Expression[] params, final ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(@NotNull final Expression[] params, final ExpressionContext context) {
    return null;
  }
}
