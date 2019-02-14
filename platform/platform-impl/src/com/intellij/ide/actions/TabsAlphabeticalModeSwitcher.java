// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.tabs.newImpl.JBEditorTabs;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class TabsAlphabeticalModeSwitcher extends ToggleAction {
  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return Registry.is(JBEditorTabs.TABS_ALPHABETICAL_KEY);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    JBEditorTabs.setAlphabeticalMode(state);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    final int place = UISettings.getInstance().getEditorTabPlacement();
    e.getPresentation().setEnabled(UISettings.getInstance().getScrollTabLayoutInEditor()
                                   || place == SwingConstants.LEFT
                                   || place == SwingConstants.RIGHT);
  }
}
