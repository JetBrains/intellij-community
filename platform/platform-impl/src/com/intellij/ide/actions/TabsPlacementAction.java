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

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class TabsPlacementAction extends ToggleAction implements DumbAware {
  abstract int getPlace();

  @Override
  public boolean isSelected(AnActionEvent e) {
    return UISettings.getInstance().EDITOR_TAB_PLACEMENT == getPlace();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    UISettings.getInstance().EDITOR_TAB_PLACEMENT = getPlace();
    LafManager.getInstance().repaintUI();
    UISettings.getInstance().fireUISettingsChanged();
  }

  public static class Top    extends TabsPlacementAction {int getPlace() {return SwingConstants.TOP;}}
  public static class Left   extends TabsPlacementAction {int getPlace() {return SwingConstants.LEFT;}}
  public static class Bottom extends TabsPlacementAction {int getPlace() {return SwingConstants.BOTTOM;}}
  public static class Right  extends TabsPlacementAction {int getPlace() {return SwingConstants.RIGHT;}}
  public static class None   extends TabsPlacementAction {int getPlace() {return UISettings.TABS_NONE;}}
}
