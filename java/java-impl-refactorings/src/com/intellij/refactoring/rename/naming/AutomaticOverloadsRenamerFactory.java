// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.rename.naming;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class AutomaticOverloadsRenamerFactory implements AutomaticRenamerFactory {

  @Override
  public boolean isApplicable(@NotNull PsiElement element) {
    if (element.getLanguage() == JavaLanguage.INSTANCE && element instanceof PsiMethod && !((PsiMethod)element).isConstructor()) {
      final PsiClass containingClass = ((PsiMethod)element).getContainingClass();
      return containingClass != null && containingClass.findMethodsByName(((PsiMethod)element).getName(), false).length > 1;
    }
    return false;
  }

  @Override
  public String getOptionName() {
    return JavaRefactoringBundle.message("rename.overloads");
  }

  @Override
  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isRenameOverloads();
  }

  @Override
  public void setEnabled(boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameOverloads(enabled);
  }

  @Override
  public @NotNull AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new AutomaticOverloadsRenamer((PsiMethod)element, newName);
  }
}