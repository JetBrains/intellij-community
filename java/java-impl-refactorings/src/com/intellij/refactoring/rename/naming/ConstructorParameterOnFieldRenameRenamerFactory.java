// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class ConstructorParameterOnFieldRenameRenamerFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(@NotNull PsiElement element) {
    return element instanceof PsiField;
  }

  @Override
  public String getOptionName() {
    return null;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void setEnabled(boolean enabled) { }

  @Override
  public @NotNull AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new ConstructorParameterOnFieldRenameRenamer((PsiField) element, newName);
  }
}