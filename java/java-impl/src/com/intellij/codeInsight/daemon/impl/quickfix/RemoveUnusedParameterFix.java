/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveUnusedParameterFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myName;

  public RemoveUnusedParameterFix(PsiParameter parameter) {
    super(parameter);
    myName = parameter.getName();
  }

  @NotNull
  @Override
  public String getText() {
    return CommonQuickFixBundle.message("fix.remove.title.x", JavaElementKind.PARAMETER.object(), myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.unused.element.family", JavaElementKind.PARAMETER.object());
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiParameter myParameter = (PsiParameter)startElement;
    return
      myParameter.getDeclarationScope() instanceof PsiMethod
      && BaseIntentionAction.canModify(myParameter);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiParameter myParameter = (PsiParameter)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(myParameter.getContainingFile())) return;
    removeReferences(myParameter);
  }

  public static void removeReferences(PsiParameter parameter) {
    PsiMethod method = (PsiMethod) parameter.getDeclarationScope();
    var provider = JavaSpecialRefactoringProvider.getInstance();
    var processor = provider.getChangeSignatureProcessorWithCallback(
      parameter.getProject(),
      method,
      false, null,
      method.getName(),
      method.getReturnType(),
      ParameterInfoImpl.fromMethodExceptParameter(method, parameter),
      true,
      null
    );
    processor.run();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
