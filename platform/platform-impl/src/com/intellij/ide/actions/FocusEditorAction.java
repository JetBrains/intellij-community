// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class FocusEditorAction extends AnAction {

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e.getDataContext()));
  }

  private static boolean isEnabled(@NotNull DataContext e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return false;
    }
    return !ToolWindowManager.getInstance(project).isEditorComponentActive();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    ToolWindowManager.getInstance(project).activateEditorComponent();
  }
}
