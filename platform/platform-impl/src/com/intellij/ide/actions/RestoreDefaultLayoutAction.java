// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager;
import org.jetbrains.annotations.NotNull;

public final class RestoreDefaultLayoutAction extends RestoreNamedLayoutAction {
  public RestoreDefaultLayoutAction() {
    super(ToolWindowDefaultLayoutManager.DEFAULT_LAYOUT_NAME);
  }
}
