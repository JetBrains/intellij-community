/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
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
    return QuickFixBundle.message("remove.unused.parameter.text", myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.unused.parameter.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiParameter myParameter = (PsiParameter)startElement;
    return
      myParameter.getDeclarationScope() instanceof PsiMethod
      && myParameter.getManager().isInProject(myParameter);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiParameter myParameter = (PsiParameter)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(myParameter.getContainingFile())) return;
    removeReferences(myParameter);
  }

  public static void removeReferences(PsiParameter parameter) {
    PsiMethod method = (PsiMethod) parameter.getDeclarationScope();
    ChangeSignatureProcessor processor = new ChangeSignatureProcessor(parameter.getProject(),
                                                                      method,
                                                                      false, null,
                                                                      method.getName(),
                                                                      method.getReturnType(),
                                                                      ParameterInfoImpl.getParameterInfosAfterRemoval(method, parameter));
    processor.run();
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
