// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ShowTabsInSingleRowAction extends ToggleAction implements DumbAware {
  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return UISettings.getInstance().getScrollTabLayoutInEditor();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    UISettings.getInstance().getState().setScrollTabLayoutInEditor(state);
    LafManager.getInstance().repaintUI();
    UISettings.getInstance().fireUISettingsChanged();
  }
}
