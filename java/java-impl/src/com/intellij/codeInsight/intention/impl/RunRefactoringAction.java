// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class RunRefactoringAction extends BaseRefactoringIntentionAction {
  private final RefactoringActionHandler myHandler;
  private final @IntentionName String myCommandName;

  public RunRefactoringAction(RefactoringActionHandler handler, @IntentionName String commandName) {
    myHandler = handler;
    myCommandName = commandName;
  }

  @Override
  public @NotNull String getText() {
    return myCommandName;
  }

  @Override
  public final @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    myHandler.invoke(project, editor, element.getContainingFile(), null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
