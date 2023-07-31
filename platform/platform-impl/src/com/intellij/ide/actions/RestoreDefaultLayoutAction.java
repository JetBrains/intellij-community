// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager;
import org.jetbrains.annotations.NotNull;

public final class RestoreDefaultLayoutAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
    String activeLayout = ToolWindowDefaultLayoutManager.getInstance().getActiveLayoutName();
    e.getPresentation().setDescription(
      ToolWindowDefaultLayoutManager.FACTORY_DEFAULT_LAYOUT_NAME.equals(activeLayout)
      ? ActionsBundle.message("action.RestoreFactoryDefaultLayout.description")
      : ActionsBundle.message("action.RestoreDefaultLayout.named.description", activeLayout)
    );
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var project = e.getProject();
    if (project == null) {
      return;
    }
    ToolWindowManagerEx.getInstanceEx(project).setLayout(ToolWindowDefaultLayoutManager.getInstance().getLayoutCopy());
  }

}
