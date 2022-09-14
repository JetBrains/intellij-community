// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import org.jetbrains.annotations.NotNull;

public class IntroduceConstantFix implements LocalQuickFix {

  private final @NlsActions.ActionText String myFamilyName;

  public IntroduceConstantFix() { 
    myFamilyName = IntroduceConstantHandler.getRefactoringNameText(); 
  }

  public IntroduceConstantFix(@NlsActions.ActionText String familyName) {
    myFamilyName = familyName;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myFamilyName;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiExpression)) return;

    doIntroduce(project, (PsiExpression)element);
  }

  protected void doIntroduce(@NotNull Project project, PsiExpression element) {
    PsiExpression[] expressions = {element};
    new IntroduceConstantHandler().invoke(project, expressions);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    PsiElement element = previewDescriptor.getPsiElement();
    if (!(element instanceof PsiExpression)) return IntentionPreviewInfo.EMPTY;
    applyFix(project, previewDescriptor);
    return IntentionPreviewInfo.DIFF;
  }
}
