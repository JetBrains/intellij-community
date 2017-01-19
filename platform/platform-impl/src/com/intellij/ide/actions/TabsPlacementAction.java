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
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class TabsPlacementAction extends ToggleAction implements DumbAware {

  protected TabsPlacementAction(@Nullable final String text, @Nullable final String description) {
    super(text, description, null);
  }

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

  public static class Top extends TabsPlacementAction {
    private static final String TEXT_PLACEMENT_TOP = ActionsBundle.message("action.TabsPlacementAction.top.text");
    private static final String DESCRIPTION_PLACEMENT_TOP = ActionsBundle.message("action.TabsPlacementAction.top.description");

    public Top() {
      super(TEXT_PLACEMENT_TOP, DESCRIPTION_PLACEMENT_TOP);
    }

    @Override
    int getPlace() {
      return SwingConstants.TOP;
    }
  }

  public static class Left extends TabsPlacementAction {
    private static final String TEXT_PLACEMENT_LEFT = ActionsBundle.message("action.TabsPlacementAction.left.text");
    private static final String DESCRIPTION_PLACEMENT_LEFT = ActionsBundle.message("action.TabsPlacementAction.left.description");

    public Left() {
      super(TEXT_PLACEMENT_LEFT, DESCRIPTION_PLACEMENT_LEFT);
    }

    @Override
    int getPlace() {
      return SwingConstants.LEFT;
    }
  }

  public static class Bottom extends TabsPlacementAction {
    private static final String TEXT_PLACEMENT_BOTTOM = ActionsBundle.message("action.TabsPlacementAction.bottom.text");
    private static final String DESCRIPTION_PLACEMENT_BOTTOM = ActionsBundle.message("action.TabsPlacementAction.bottom.description");

    public Bottom() {
      super(TEXT_PLACEMENT_BOTTOM, DESCRIPTION_PLACEMENT_BOTTOM);
    }

    @Override
    int getPlace() {
      return SwingConstants.BOTTOM;
    }
  }

  public static class Right extends TabsPlacementAction {
    private static final String TEXT_PLACEMENT_RIGHT = ActionsBundle.message("action.TabsPlacementAction.right.text");
    private static final String DESCRIPTION_PLACEMENT_RIGHT = ActionsBundle.message("action.TabsPlacementAction.right.description");

    public Right() {
      super(TEXT_PLACEMENT_RIGHT, DESCRIPTION_PLACEMENT_RIGHT);
    }

    @Override
    int getPlace() {
      return SwingConstants.RIGHT;
    }
  }

  public static class None extends TabsPlacementAction {
    private static final String TEXT_PLACEMENT_NONE = ActionsBundle.message("action.TabsPlacementAction.none.text");
    private static final String DESCRIPTION_PLACEMENT_NONE = ActionsBundle.message("action.TabsPlacementAction.none.description");

    public None() {
      super(TEXT_PLACEMENT_NONE, DESCRIPTION_PLACEMENT_NONE);
    }

    @Override
    int getPlace() {
      return UISettings.TABS_NONE;
    }
  }
}
