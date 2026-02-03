// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.messages.MessagesService;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PasswordMacro extends PromptingMacro{
  @Override
  public @NotNull String getName() {
    return "Password";
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getDescription() {
    return IdeCoreBundle.message("displays.a.password.input.dialog");
  }

  @Override
  protected @Nullable String promptUser(@NotNull DataContext dataContext, @Nullable @Nls String label, @Nullable String defaultValue) {
    String message = label == null ? IdeCoreBundle.message("dialog.message.enter.password") : label + ":";
    return MessagesService.getInstance().showPasswordDialog((Project)null, message, IdeCoreBundle.message("dialog.title.password"),
                                                            UIUtil.getQuestionIcon(), null);
  }
}
