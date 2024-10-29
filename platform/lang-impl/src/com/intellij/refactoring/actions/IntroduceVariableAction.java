// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IntroduceVariableAction extends IntroduceActionBase {
  public IntroduceVariableAction() {
    setEnabledInModalContext(true);
  }

  @Override
  protected RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider) {
    return provider.getIntroduceVariableHandler();
  }

  @Override
  protected @Nullable RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider, PsiElement element) {
    return provider.getIntroduceVariableHandler(element);
  }
}
