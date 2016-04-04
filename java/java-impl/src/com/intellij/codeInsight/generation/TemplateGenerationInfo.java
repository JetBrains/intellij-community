/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public abstract class TemplateGenerationInfo extends GenerationInfoBase implements GenerationInfo {
  private final Expression myExpression;
  private SmartPsiElementPointer<PsiMethod> myElement;

  public TemplateGenerationInfo(final PsiMethod element, final Expression expression) {
    setElement(element);
    myExpression = expression;
  }

  private void setElement(final PsiMethod element) {
    myElement = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  protected abstract PsiElement getTemplateElement(PsiMethod method);

  @Override
  public PsiMethod getPsiMember() {
    return myElement.getElement();
  }

  @Override
  public void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException {
    setElement((PsiMethod)GenerateMembersUtil.insert(aClass, myElement.getElement(), anchor, before));
  }

  public Template getTemplate() {
    PsiMethod element = getPsiMember();
    TemplateBuilderImpl builder = new TemplateBuilderImpl(element);
    builder.replaceElement(getTemplateElement(element), myExpression);
    return builder.buildTemplate();
  }
}
