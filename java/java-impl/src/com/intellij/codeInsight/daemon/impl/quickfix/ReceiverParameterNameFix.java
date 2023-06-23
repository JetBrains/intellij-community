// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiReceiverParameter;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReceiverParameterNameFix extends PsiUpdateModCommandAction<PsiReceiverParameter> {
  private final String myNewName;

  public ReceiverParameterNameFix(@NotNull PsiReceiverParameter parameter, @NotNull String newName) {
    super(parameter);
    myNewName = newName;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReceiverParameter element) {
    return Presentation.of(CommonQuickFixBundle.message("fix.replace.with.x", myNewName));
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("fix.receiver.parameter.name.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiReceiverParameter receiver, @NotNull ModPsiUpdater updater) {
    CommentTracker ct = new CommentTracker();
    ct.replaceExpressionAndRestoreComments(receiver.getIdentifier(), myNewName);
  }
}