// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ActiveWindowsWatcher;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public abstract class AbstractTraverseWindowAction extends AnAction implements ActionRemoteBehaviorSpecification.Frontend {

  protected void doPerform(@NotNull Function<? super Window, ? extends Window> mapWindow) {
    Window w = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
    if (!ActiveWindowsWatcher.isTheCurrentWindowOnTheActivatedList(w)) {
      assert w != null;
      Window window = w;
      while (SwingUtilities.getWindowAncestor(window) != null
             && window != SwingUtilities.getWindowAncestor(window))
      {
        window = SwingUtilities.getWindowAncestor(window);
      }

      if (!ActiveWindowsWatcher.isTheCurrentWindowOnTheActivatedList(window)) {
        if (AppUIUtil.isInFullScreen(window)) {
          switchFullScreenFrame((JFrame)window);
        }
        return;
      }

      Window mappedWindow = mapWindow.fun(window);
      Component recentFocusOwner = mappedWindow.getMostRecentFocusOwner();

      (recentFocusOwner == null || !recentFocusOwner.isFocusable() ? mappedWindow : recentFocusOwner).requestFocus();
    } else {
      Window mappedWindow = mapWindow.fun(w);
      Component recentFocusOwner = mappedWindow.getMostRecentFocusOwner();

      (recentFocusOwner == null || !recentFocusOwner.isFocusable() ? mappedWindow : recentFocusOwner).requestFocus();
    }
  }

  protected void switchFullScreenFrame(@NotNull JFrame frame) {
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
