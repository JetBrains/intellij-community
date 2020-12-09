// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

final class MinimizeCurrentWindowAction extends MacWindowActionBase {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Component focusOwner = IdeFocusManager.getGlobalInstance().getFocusOwner();
    if (focusOwner != null) {
      final Window window = focusOwner instanceof JFrame ? (Window) focusOwner : SwingUtilities.getWindowAncestor(focusOwner);
      if (window instanceof JFrame && !(((JFrame)window).getState() == Frame.ICONIFIED)) {
        ((JFrame)window).setState(Frame.ICONIFIED);
      }
    }
  }
}
