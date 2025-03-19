// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class AutomaticParametersRenamerFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(@NotNull PsiElement element) {
    if (element instanceof PsiParameter) {
      PsiElement declarationScope = ((PsiParameter)element).getDeclarationScope();
      if (declarationScope instanceof PsiMethod && !((PsiMethod)declarationScope).hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getOptionName() {
    return RefactoringBundle.message("rename.parameters.hierarchy");
  }

  @Override
  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isRenameParameterInHierarchy();
  }

  @Override
  public void setEnabled(boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameParameterInHierarchy(enabled);
  }

  @Override
  public @NotNull AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new AutomaticParametersRenamer((PsiParameter)element, newName);
  }
}