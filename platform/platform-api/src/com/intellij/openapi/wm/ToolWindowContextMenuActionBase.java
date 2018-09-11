// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ToolWindowContextMenuActionBase extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ToolWindow toolWindow = ToolWindowManager.getActiveToolWindow();
    if (toolWindow == null)
      return;
    Content selectedContent = toolWindow.getContentManager().getSelectedContent();
    actionPerformed(e, toolWindow, selectedContent);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    final ToolWindow toolWindow = ToolWindowManager.getActiveToolWindow();
    if (toolWindow == null)
      return;
    Content selectedContent = toolWindow.getContentManager().getSelectedContent();
    update(e, toolWindow, selectedContent);
  }

  public abstract void update(@NotNull AnActionEvent e, @NotNull ToolWindow activeToolWindow, @Nullable Content selectedContent);
  public abstract void actionPerformed(@NotNull AnActionEvent e, @NotNull ToolWindow activeToolWindow, @Nullable Content selectedContent);
}
