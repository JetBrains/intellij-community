// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeVarargParameterLastFix extends PsiUpdateModCommandAction<PsiVariable> {
  public MakeVarargParameterLastFix(@NotNull PsiVariable parameter) {
    super(parameter);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiVariable parameter) {
    return Presentation.of(QuickFixBundle.message("make.vararg.parameter.last.text", parameter.getName()));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("make.vararg.parameter.last.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiVariable parameter, @NotNull ModPsiUpdater updater) {
    parameter.getParent().add(parameter);
    parameter.delete();
  }
}
