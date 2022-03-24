
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

public class ViewToolbarAction extends ToggleAction implements DumbAware {
  @Override
  public boolean isSelected(@NotNull AnActionEvent event) {
    return UISettings.getInstance().getShowMainToolbar();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean state) {
    UISettings uiSettings = UISettings.getInstance();
    uiSettings.setShowMainToolbar(state);
    uiSettings.fireUISettingsChanged();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabledAndVisible(!ExperimentalUI.isNewToolbar());
  }
}
