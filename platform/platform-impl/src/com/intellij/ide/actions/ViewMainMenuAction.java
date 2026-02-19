// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class ViewMainMenuAction extends ToggleAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return UISettings.getInstance().getShowMainMenu();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    UISettings uiSettings = UISettings.getInstance();
    uiSettings.setShowMainMenu(state);
    uiSettings.fireUISettingsChanged();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    boolean makesSense = !ExperimentalUI.isNewUI() && (SystemInfo.isWindows || (SystemInfo.isLinux));
    e.getPresentation().setEnabledAndVisible(makesSense);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
