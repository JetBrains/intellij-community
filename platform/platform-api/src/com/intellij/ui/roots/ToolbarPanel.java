/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui.roots;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 */
public class ToolbarPanel extends JPanel {
  /**
   * @deprecated use {@link #ToolbarPanel(JComponent, ActionGroup, String, JComponent)} instead and specify action place and target component for toolbar explicitly
   */
  @Deprecated(forRemoval = true)
  public ToolbarPanel(JComponent contentComponent, ActionGroup actions) {
    this(contentComponent, actions, ActionPlaces.UNKNOWN);
  }

  /**
   * @deprecated use {@link #ToolbarPanel(JComponent, ActionGroup, String, JComponent)} instead and specify action place and target component for toolbar explicitly
   */
  @Deprecated
  public ToolbarPanel(@NotNull JComponent contentComponent, @NotNull ActionGroup actions, @NotNull @NonNls String toolbarPlace) {
    this(contentComponent, actions, toolbarPlace, null);
  }

  public ToolbarPanel(@NotNull JComponent contentComponent,
                      @NotNull ActionGroup actions,
                      @NotNull @NonNls String toolbarPlace,
                      @Nullable JComponent targetComponent) {
    super(new GridBagLayout());
    setBorder(BorderFactory.createEtchedBorder());
    if (contentComponent.getBorder() != null) {
      contentComponent.setBorder(BorderFactory.createEmptyBorder());
    }
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(toolbarPlace, actions, true);
    if (targetComponent != null) {
      actionToolbar.setTargetComponent(targetComponent);
    }

    add(actionToolbar.getComponent(),
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                               new Insets(0, 0, 0, 0), 0, 0));
    add(contentComponent,
        new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                               new Insets(0, 0, 0, 0), 0, 0));
  }
}
