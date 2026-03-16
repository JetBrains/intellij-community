// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InsertMissingTokenFix implements ModCommandAction {
  private final @NotNull String myToken;
  private final boolean myMoveAfter;

  public InsertMissingTokenFix(@NotNull String token) {
    this(token, false);
  }

  public InsertMissingTokenFix(@NotNull String token, boolean moveAfter) {
    myToken = token;
    myMoveAfter = moveAfter;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return Presentation.of(getFamilyName())
      .withPriority(PriorityAction.Priority.LOW)
      .withFixAllOption(this, action -> action instanceof InsertMissingTokenFix tokenFix && tokenFix.myToken.equals(myToken));
  }

  @Override
  public @NotNull String getFamilyName() {
    return CommonQuickFixBundle.message("fix.insert.x", myToken);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.insertText(context, myToken, myMoveAfter);
  }
}
