/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.tabs.impl.JBEditorTabs;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class TabsAlphabeticalModeSwitcher extends ToggleAction {
  @Override
  public boolean isSelected(AnActionEvent e) {
    return Registry.is(JBEditorTabs.TABS_ALPHABETICAL_KEY);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    JBEditorTabs.setAlphabeticalMode(state);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final int place = UISettings.getInstance().EDITOR_TAB_PLACEMENT;
    e.getPresentation().setEnabled(UISettings.getInstance().SCROLL_TAB_LAYOUT_IN_EDITOR
                                   || place == SwingConstants.LEFT
                                   || place == SwingConstants.RIGHT);
  }
}
