// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager;
import org.jetbrains.annotations.NotNull;

public final class RestoreDefaultLayoutAction extends RestoreNamedLayoutAction {

  public RestoreDefaultLayoutAction() {
    super(() -> ToolWindowDefaultLayoutManager.getInstance().getActiveLayoutName());
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return false; // Restore Current Layout in the main Layouts menu looks weird with a check mark
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setDescription(ActionsBundle.message("action.RestoreDefaultLayout.description", getLayoutNameSupplier().invoke()));
  }

}
