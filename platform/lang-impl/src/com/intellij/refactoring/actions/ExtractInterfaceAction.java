// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExtractInterfaceAction extends ExtractSuperActionBase {
  public ExtractInterfaceAction() {
    setInjectedContext(true);
  }

  @Override
  protected @Nullable RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider supportProvider) {
    return supportProvider.getExtractInterfaceHandler();
  }
}