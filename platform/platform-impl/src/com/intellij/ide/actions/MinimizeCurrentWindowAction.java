// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

final class MinimizeCurrentWindowAction extends MacWindowActionBase {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (window instanceof Frame) {
      ((Frame)window).setState(Frame.ICONIFIED);
    }
  }
}
