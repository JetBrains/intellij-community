// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ActiveWindowsWatcher;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.mac.MacWinTabsHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public final class NextWindow extends AbstractTraverseWindowAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    doPerform(w -> ActiveWindowsWatcher.nextWindowAfter(w));
  }

  @Override
  protected void switchFullScreenFrame(@NotNull JFrame frame) {
    MacWinTabsHandler.switchFrameIfPossible(frame, true);
  }
}
