// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;


import com.intellij.ide.ActiveWindowsWatcher;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.mac.MacWinTabsHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class PreviousWindow extends AbstractTraverseWindowAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    doPerform(w -> ActiveWindowsWatcher.nextWindowBefore(w));
  }

  @Override
  protected void switchFullScreenFrame(@NotNull JFrame frame) {
    MacWinTabsHandler.switchFrameIfPossible(frame, false);
  }
}
