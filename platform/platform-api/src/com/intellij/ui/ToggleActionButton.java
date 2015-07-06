/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class ToggleActionButton extends AnActionButton implements Toggleable {
  @SuppressWarnings("NullableProblems")
  public ToggleActionButton(String text, Icon icon) {
    super(text, null, icon);
  }

  /**
   * Returns the selected (checked, pressed) state of the action.
   * @param e the action event representing the place and context in which the selected state is queried.
   * @return true if the action is selected, false otherwise
   */
  public abstract boolean isSelected(AnActionEvent e);

  /**
   * Sets the selected state of the action to the specified value.
   * @param e     the action event which caused the state change.
   * @param state the new selected state of the action.
   */
  public abstract void setSelected(AnActionEvent e, boolean state);

  @Override
  public final void actionPerformed(AnActionEvent e) {
    final boolean state = !isSelected(e);
    setSelected(e, state);
    final Boolean selected = state;
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty(Toggleable.SELECTED_PROPERTY, selected);
  }

  @Override
  public final void updateButton(AnActionEvent e) {
    final Boolean selected = isSelected(e);
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty(Toggleable.SELECTED_PROPERTY, selected);
  }
}
