// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.actions;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dennis.Ushakov
 */
public final class ExtractModuleAction extends ExtractSuperActionBase {

  @Override
  protected RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider supportProvider) {
    return supportProvider.getExtractModuleHandler();
  }
}