// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.toolbar.experimental;

import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

final class ViewNewToolbarAction extends ToggleAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public boolean isSelected(@NotNull AnActionEvent event) {
    return ToolbarSettings.getInstance().isVisible();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event,
                          boolean isVisible) {
    ToolbarSettings.getInstance().setVisible(isVisible);
    UISettings.getInstance().fireUISettingsChanged();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabledAndVisible(ToolbarSettings.getInstance().isAvailable());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
