// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    return CommonQuickFixBundle.message("fix.insert.x", myToken);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    String oldText = context.file().getText();
    int offset = context.offset();
    String newText = oldText.substring(0, offset) + myToken + oldText.substring(offset);
    return new ModUpdateFileText(context.file().getVirtualFile(), oldText, newText,
                                 List.of(new ModUpdateFileText.Fragment(offset, 0, myToken.length())));
  }
}
