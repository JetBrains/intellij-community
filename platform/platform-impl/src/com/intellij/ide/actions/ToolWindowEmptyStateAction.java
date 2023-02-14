// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.JBColor;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentUI;
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
  protected boolean hasEmptyState(@NotNull Project project) {
    return true;
  }

  @Override
  protected void createEmptyState(@NotNull Project project) {
    ensureToolWindowCreated(project);
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(getToolWindowId());
    if (toolWindow == null) return;
    toolWindow.setAvailable(true);
    if (toolWindow instanceof ToolWindowImpl windowImpl) {
      StatusText emptyText = windowImpl.getEmptyText();
      setupEmptyText(project, emptyText);
      setEmptyStateBackground(toolWindow);
    }
    rebuildContentUi(toolWindow);
    toolWindow.show();
    toolWindow.activate(null, true);
  }

  protected abstract void setupEmptyText(@NotNull Project project, @NotNull StatusText text);

  protected abstract void ensureToolWindowCreated(@NotNull Project project);

  public static void setEmptyStateBackground(@NotNull ToolWindow toolWindow) {
    if (toolWindow instanceof ToolWindowImpl) {
      ((ToolWindowImpl)toolWindow).setEmptyStateBackground(JBColor.background());
    }
  }

  public static void rebuildContentUi(@NotNull ToolWindow toolWindow) {
    ContentManager manager = toolWindow.getContentManager();
    if (manager instanceof ContentManagerImpl) {
      ContentUI ui = ((ContentManagerImpl)manager).getUI();
      if (ui instanceof ToolWindowContentUi) {
        ((ToolWindowContentUi)ui).rebuild();
      }
    }
  }
}
