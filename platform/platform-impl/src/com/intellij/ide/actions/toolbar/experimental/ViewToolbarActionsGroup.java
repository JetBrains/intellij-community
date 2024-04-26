// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.toolbar.experimental;

import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public final class ViewToolbarActionsGroup extends DefaultActionGroup implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isEnabled = !ToolbarSettings.getInstance().isAvailable();
    e.getPresentation().setEnabledAndVisible(isEnabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
