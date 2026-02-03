// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.terminal.JBTerminalWidget;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class TerminalBaseContextAction extends DumbAwareAction {

  public TerminalBaseContextAction() {
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    JBTerminalWidget terminal = getTerminalWidget(e);
    e.getPresentation().setEnabledAndVisible(terminal != null);
  }

  protected static @Nullable JBTerminalWidget getTerminalWidget(@NotNull AnActionEvent e) {
    return e.getDataContext().getData(JBTerminalWidget.TERMINAL_DATA_KEY);
  }
}
