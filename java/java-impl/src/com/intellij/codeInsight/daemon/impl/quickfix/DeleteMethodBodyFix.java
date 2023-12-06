// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeleteMethodBodyFix extends PsiUpdateModCommandAction<PsiMethod> {
  public DeleteMethodBodyFix(@NotNull PsiMethod method) {
    super(method);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("delete.body.text");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethod method) {
    return method.getBody() == null ? null : Presentation.of(getFamilyName()).withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethod method, @NotNull ModPsiUpdater updater) {
    final PsiCodeBlock body = method.getBody();
    assert body != null;
    body.delete();
  }
}
