// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ActiveWindowsWatcher;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.AppUIUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Window;
import java.util.function.Function;

@Internal
public abstract class AbstractTraverseWindowAction extends AnAction implements ActionRemoteBehaviorSpecification.Frontend {
  protected void doPerform(@NotNull Function<? super Window, ? extends Window> mapWindow) {
    Window w = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
    if (w != null && !ActiveWindowsWatcher.INSTANCE.isTheCurrentWindowOnTheActivatedList(w)) {
      Window window = w;
      while (SwingUtilities.getWindowAncestor(window) != null
             && window != SwingUtilities.getWindowAncestor(window)) {
        window = SwingUtilities.getWindowAncestor(window);
      }

      if (!ActiveWindowsWatcher.INSTANCE.isTheCurrentWindowOnTheActivatedList(window)) {
        if (AppUIUtil.isInFullScreen(window)) {
          switchFullScreenFrame((JFrame)window);
        }
        return;
      }

      Window mappedWindow = mapWindow.apply(window);
      Component recentFocusOwner = mappedWindow.getMostRecentFocusOwner();

      (recentFocusOwner == null || !recentFocusOwner.isFocusable() ? mappedWindow : recentFocusOwner).requestFocus();
    }
    else {
      Window mappedWindow = mapWindow.apply(w);
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
