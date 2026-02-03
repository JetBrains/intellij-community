// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import org.jetbrains.annotations.NotNull;

public class ReplaceMethodWithMethodObjectAction extends BaseJavaRefactoringAction{
  @Override
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isEnabledOnElements(final PsiElement @NotNull [] elements) {
    return false;
  }

  @Override
  protected RefactoringActionHandler getHandler(final @NotNull DataContext dataContext) {
    return new ExtractMethodObjectHandler();
  }
}