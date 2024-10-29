// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

@ApiStatus.Internal
@SuppressWarnings("HardCodedStringLiteral")
public final class TestMessageBoxAction extends AnAction {
  private final Random myRandom = new Random();

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    int r = myRandom.nextInt(10);
    if (r < 3) {
      String message = wrap("Test error message.", r);
      Messages.showErrorDialog(message, "Test");
    }
    else if (r < 6) {
      String message = wrap("Test warning message.", r);
      Messages.showWarningDialog(message, "Test");
    }
    else {
      String message = wrap("Test info message.", r);
      Messages.showInfoMessage(message, "Test");
    }
  }

  private static String wrap(String s, int r) {
    return r % 2 == 0 ? s : "<html><body><i>" + StringUtil.repeat(s + "<br>", 10) + "</i></body></html>";
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
