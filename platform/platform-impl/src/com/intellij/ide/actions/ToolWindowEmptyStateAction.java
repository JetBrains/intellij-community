// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.impl.ContentManagerImpl;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class ToolWindowEmptyStateAction extends ActivateToolWindowAction {
  protected ToolWindowEmptyStateAction(@NotNull String toolWindowId, Icon icon) {
    super(toolWindowId);
    getTemplatePresentation().setIcon(icon);
  }

  @Override
  protected boolean hasEmptyState() {
    return true;
  }

  @Override
  protected void createEmptyState(@NotNull Project project) {
    ensureToolWindowCreated(project);
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(getToolWindowId());
    if (toolWindow == null) return;
    toolWindow.setAvailable(true);
    StatusText emptyText = toolWindow.getEmptyText();
    if (emptyText != null) {
      setupEmptyText(project, emptyText);
      ((ToolWindowImpl) toolWindow).setEmptyStateBackground(JBColor.background());
    }
    ContentManagerImpl manager = (ContentManagerImpl) toolWindow.getContentManager();
    manager.rebuildContentUi();
    toolWindow.show();
    toolWindow.activate(null, true);
  }

  protected abstract void setupEmptyText(@NotNull Project project, @NotNull StatusText text);

  protected abstract void ensureToolWindowCreated(@NotNull Project project);

}
