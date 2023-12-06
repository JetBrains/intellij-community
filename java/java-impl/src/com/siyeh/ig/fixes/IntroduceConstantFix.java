// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import org.jetbrains.annotations.NotNull;

public class IntroduceConstantFix extends RefactoringInspectionGadgetsFix {

  private final @NlsActions.ActionText String myFamilyName;

  public IntroduceConstantFix() { 
    myFamilyName = RefactoringBundle.message("introduce.constant.title");
  }

  public IntroduceConstantFix(@NlsActions.ActionText String familyName) {
    myFamilyName = familyName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myFamilyName;
  }

  @NotNull
  @Override
  public RefactoringActionHandler getHandler() {
    return JavaRefactoringActionHandlerFactory.getInstance().createIntroduceConstantHandler();
  }

  @Override
  public PsiElement getElementToRefactor(PsiElement element) {
    return element;
  }

  @Override
  public void doFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element instanceof PsiExpression expression) {
      doIntroduce(project, expression);
    }
  }

  protected void doIntroduce(@NotNull Project project, PsiExpression element) {
    var handler = (JavaIntroduceVariableHandlerBase)getHandler();
    handler.invoke(project, null, element);
  }
}
