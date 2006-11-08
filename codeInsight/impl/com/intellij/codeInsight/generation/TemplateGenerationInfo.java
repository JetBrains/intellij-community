package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

abstract class TemplateGenerationInfo implements GenerationInfo {
  private final Expression myExpression;
  private SmartPsiElementPointer<PsiMethod> myElement;

  public TemplateGenerationInfo(final PsiMethod element, final Expression expression) {
    setElement(element);
    myExpression = expression;
  }

  private void setElement(final PsiMethod element) {
    myElement = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  protected abstract PsiTypeElement getTemplateElement(PsiMethod method);

  @NotNull
  public PsiMethod getPsiMember() {
    return myElement.getElement();
  }

  public void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException {
    setElement((PsiMethod)GenerateMembersUtil.insert(aClass, myElement.getElement(), anchor, before));
  }

  public Template getTemplate() {
    PsiMethod element = getPsiMember();
    TemplateBuilder builder = new TemplateBuilder(element);
    builder.replaceElement(getTemplateElement(element), myExpression);
    return builder.buildTemplate();
  }
}
