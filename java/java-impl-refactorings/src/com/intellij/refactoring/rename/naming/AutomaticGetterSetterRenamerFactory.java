// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.naming;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class AutomaticGetterSetterRenamerFactory implements AutomaticRenamerFactory {

  @Override
  public boolean isApplicable(@NotNull PsiElement element) {
    return element instanceof PsiField && JavaLanguage.INSTANCE.equals(element.getLanguage());
  }

  @Override
  public String getOptionName() {
    return JavaRefactoringBundle.message("rename.accessors");
  }

  @Override
  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isToRenameAccessors();
  }

  @Override
  public void setEnabled(boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameAccessors(enabled);
  }

  @Override
  public @NotNull AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new AutomaticGetterSetterRenamer(element, newName);
  }
}