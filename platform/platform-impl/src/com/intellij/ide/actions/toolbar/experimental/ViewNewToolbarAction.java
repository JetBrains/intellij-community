// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.toolbar.experimental;

import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.ide.ui.experimental.toolbar.ExperimentalToolbarSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

public class ViewNewToolbarAction extends ToggleAction implements DumbAware {

  @Override
  public boolean isSelected(@NotNull AnActionEvent event) {
    var toolbarService = ToolbarSettings.Companion.getInstance();
    if (toolbarService instanceof ExperimentalToolbarSettings) {
      return ((ExperimentalToolbarSettings)toolbarService).getShowNewToolbar();
    }
    return false;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean state) {
    var toolbarService = ToolbarSettings.Companion.getInstance();
    if(toolbarService instanceof ExperimentalToolbarSettings) {
      UISettings uiSettings = UISettings.getInstance();
      ((ExperimentalToolbarSettings)toolbarService).setShowNewToolbar(state);
      uiSettings.fireUISettingsChanged();
    }
  }
}
