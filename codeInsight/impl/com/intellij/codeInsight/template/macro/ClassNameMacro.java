package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class ClassNameMacro implements Macro {

  public String getName() {
    return "className";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.classname");
  }

  public String getDefaultValue() {
    return "";
  }

  public Result calculateResult(Expression[] params, final ExpressionContext context) {
    Project project = context.getProject();
    int templateStartOffset = context.getTemplateStartOffset();
    int offset = templateStartOffset > 0 ? context.getTemplateStartOffset() - 1 : context.getTemplateStartOffset();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement place = file.findElementAt(offset);
    PsiClass aClass = null;

    while(place != null){
      if (place instanceof PsiClass && !(place instanceof PsiAnonymousClass) && !(place instanceof PsiTypeParameter)){
        aClass = (PsiClass)place;
        // if className() is evaluated outside of the body of inner class, return name of its outer class instead (IDEADEV-19865)
        final PsiJavaToken lBrace = aClass.getLBrace();
        if (lBrace != null && offset < lBrace.getTextOffset() && aClass.getContainingClass() != null) {
          aClass = aClass.getContainingClass();
        }
        break;
      }
      if (place instanceof PsiJavaFile){
        PsiClass[] classes = ((PsiJavaFile)place).getClasses();
        aClass = classes.length != 0 ? classes[0] : null;
        break;
      }
      place = place.getParent();
    }

    if (aClass == null) return null;
    String result = aClass.getName();
    while (aClass.getContainingClass() != null && aClass.getContainingClass().getName() != null) {
      result = aClass.getContainingClass().getName() + "$" + result;
      aClass = aClass.getContainingClass();
    }
    return new TextResult(result);
  }

  public Result calculateQuickResult(Expression[] params, ExpressionContext context) {
    return null;
  }

  public LookupItem[] calculateLookupItems(Expression[] params, final ExpressionContext context) {
    return null;
  }
}
