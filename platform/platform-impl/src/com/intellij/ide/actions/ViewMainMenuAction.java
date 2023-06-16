// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.GlobalMenuLinux;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ViewMainMenuAction extends ToggleAction implements DumbAware {
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
    boolean makesSense = !ExperimentalUI.isNewUI() && (SystemInfo.isWindows || (SystemInfo.isLinux && !GlobalMenuLinux.isPresented()));
    e.getPresentation().setEnabledAndVisible(makesSense);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
