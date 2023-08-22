// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

final class MinimizeCurrentWindowAction extends MacWindowActionBase {
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (window instanceof Frame) {
      ((Frame)window).setState(Frame.ICONIFIED);
    }
  }
}
