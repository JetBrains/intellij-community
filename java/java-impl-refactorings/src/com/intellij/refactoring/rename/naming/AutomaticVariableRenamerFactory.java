// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.naming;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public final class AutomaticVariableRenamerFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(@NotNull PsiElement element) {
    return element instanceof PsiClass && !(element instanceof PsiAnonymousClass);
  }

  @Override
  public String getOptionName() {
    return JavaRefactoringBundle.message("rename.variables");
  }

  @Override
  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isToRenameVariables();
  }

  @Override
  public void setEnabled(boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameVariables(enabled);
  }

  @Override
  public @NotNull AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new AutomaticVariableRenamer((PsiClass)element, newName, usages);
  }
}