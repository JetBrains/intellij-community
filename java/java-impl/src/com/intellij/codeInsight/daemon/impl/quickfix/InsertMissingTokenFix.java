// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.ide.IdeBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InsertMissingTokenFix implements ModCommandAction {
  private final String myToken;

  public InsertMissingTokenFix(String token) {
    myToken = token;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return Presentation.of(getFamilyName())
      .withPriority(PriorityAction.Priority.LOW)
      .withFixAllOption(this, action -> action instanceof InsertMissingTokenFix tokenFix && tokenFix.myToken.equals(myToken));
  }

  @Override
  public @NotNull String getFamilyName() {
    return IdeBundle.message("quickfix.text.insert.0", myToken);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    return ModCommand.psiUpdate(context.file(), f -> f.getViewProvider().getDocument().insertString(context.offset(), myToken));
  }
}
