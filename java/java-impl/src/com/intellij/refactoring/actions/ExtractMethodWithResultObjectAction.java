// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import com.intellij.refactoring.extractMethodWithResultObject.ExtractMethodWithResultObjectHandler;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodWithResultObjectAction extends BaseRefactoringAction{
  @Override
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isEnabledOnElements(@NotNull final PsiElement[] elements) {
    return false;
  }

  @Override
  protected RefactoringActionHandler getHandler(@NotNull final DataContext dataContext) {
    return new ExtractMethodWithResultObjectHandler();
  }
}