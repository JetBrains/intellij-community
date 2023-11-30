// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;

public final class ExtractSuperclassAction extends ExtractSuperActionBase {
  public ExtractSuperclassAction() {
    setInjectedContext(true);
  }

  @Override
  protected RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider supportProvider) {
    return supportProvider.getExtractSuperClassHandler();
  }
}