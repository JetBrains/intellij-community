// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public final class ViewToolWindowButtonsAction extends ToggleAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public boolean isSelected(@NotNull AnActionEvent event) {
    return !UISettings.getInstance().getHideToolStripes();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean state) {
    UISettings uiSettings = UISettings.getInstance();
    uiSettings.setHideToolStripes(!state);
    uiSettings.fireUISettingsChanged();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
