// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author yole
 */
public class AutomaticVariableRenamerFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(@NotNull PsiElement element) {
    return element instanceof PsiClass && !(element instanceof PsiAnonymousClass);
  }

  @Override
  public String getOptionName() {
    return RefactoringBundle.message("rename.variables");
  }

  @Override
  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isToRenameVariables();
  }

  @Override
  public void setEnabled(boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameVariables(enabled);
  }

  @NotNull
  @Override
  public AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new AutomaticVariableRenamer((PsiClass)element, newName, usages);
  }
}