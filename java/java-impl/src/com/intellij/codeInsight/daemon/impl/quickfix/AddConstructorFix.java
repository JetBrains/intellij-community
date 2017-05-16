/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class AddConstructorFix implements LocalQuickFix, IntentionAction {

  private final SmartPsiElementPointer<PsiClass> myBeanClass;
  private final List<PsiParameter> myParameters;
  private final String name;

  public AddConstructorFix(PsiClass beanClass, List<PsiParameter> parameters) {
    myBeanClass = SmartPointerManager.getInstance(beanClass.getProject()).createSmartPsiElementPointer(beanClass);
    myParameters = parameters;
    final String params = myParameters.stream().map(p -> p.getText()).collect(Collectors.joining(", "));
    final String signature = beanClass.getName() + "(" + params + ")";
    name = QuickFixBundle.message("model.create.constructor.quickfix.message", signature);
  }

  @NotNull
  public String getName() {
    return name;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return name;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("model.create.constructor.quickfix.message.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    applyFix();
  }

  private void applyFix() {
    try {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(myBeanClass.getContainingFile())) return;
      PsiClass psiClass = myBeanClass.getElement();
      if (psiClass == null) return;
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myBeanClass.getProject()).getElementFactory();

      final PsiMethod constructor = elementFactory.createConstructor();

      for (PsiParameter parameter : myParameters) {
        constructor.getParameterList().add(parameter);
      }
      psiClass.add(constructor);
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    applyFix();
  }
}
