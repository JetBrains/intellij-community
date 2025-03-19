// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PromptMacro extends PromptingMacro implements SecondQueueExpandMacro {
  @Override
  public @NotNull String getName() {
    return "Prompt";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.prompt");
  }

  @Override
  protected @Nullable String promptUser(@NotNull DataContext dataContext,
                                        @NlsContexts.DialogMessage @Nullable String label, @Nullable String defaultValue) {
    String message = label == null ? IdeCoreBundle.message("prompt.enter.parameters") : label + ":";
    return MessagesService.getInstance().showInputDialog(null, null, message, IdeCoreBundle.message("title.input"),
                                                         UIUtil.getQuestionIcon(), defaultValue, null, null, null);
  }
}
