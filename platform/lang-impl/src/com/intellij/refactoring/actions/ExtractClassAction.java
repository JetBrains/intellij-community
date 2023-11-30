// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;

import static com.intellij.refactoring.actions.ExtractSuperActionBase.removeFirstWordInMainMenu;

public final class ExtractClassAction extends BasePlatformRefactoringAction {
  @Override
  protected RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider) {
    return provider.getExtractClassHandler();
  }

  @Override
  public boolean isAvailableInEditorOnly(){
      return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    removeFirstWordInMainMenu(this, e);
  }
}