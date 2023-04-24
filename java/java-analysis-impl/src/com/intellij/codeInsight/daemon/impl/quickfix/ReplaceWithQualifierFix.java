// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.ModCommands;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceWithQualifierFix extends PsiBasedModCommandAction<PsiMethodCallExpression> {
  private final String myRole;

  public ReplaceWithQualifierFix(@NotNull PsiMethodCallExpression call, @Nullable String role) {
    super(call);
    myRole = role;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiMethodCallExpression call) {
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return ModCommands.nop();
    return ModCommands.psiUpdate(call, c -> {
      new CommentTracker().replace(c, qualifier);
    });
  }

  @Override
  public @NotNull String getName() {
    return myRole == null ? QuickFixBundle.message("replace.with.qualifier.text") :
           QuickFixBundle.message("replace.with.qualifier.text.role", myRole);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("replace.with.qualifier.text");
  }
}
