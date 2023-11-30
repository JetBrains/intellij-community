// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NavigateToAlreadyDeclaredVariableFix extends PsiBasedModCommandAction<PsiVariable> {
  public NavigateToAlreadyDeclaredVariableFix(@NotNull PsiVariable variable) {
    super(variable);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("navigate.variable.declaration.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiVariable variable) {
    return Presentation.of(QuickFixBundle.message("navigate.variable.declaration.text", variable.getName()));
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiVariable variable) {
    PsiElement element = variable.getNameIdentifier();
    return ModCommand.select(element == null ? variable : element);
  }
}
