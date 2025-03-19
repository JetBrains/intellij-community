// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceWithQualifierFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  private final String myRole;

  public ReplaceWithQualifierFix(@NotNull PsiMethodCallExpression call, @Nullable String role) {
    super(call);
    myRole = role;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call, @NotNull ModPsiUpdater updater) {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return;
    new CommentTracker().replace(call, qualifier);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression element) {
    return Presentation.of(myRole == null ? QuickFixBundle.message("replace.with.qualifier.text") :
                           QuickFixBundle.message("replace.with.qualifier.text.role", myRole));
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("replace.with.qualifier.text");
  }
}
