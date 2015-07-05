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
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * An action which has a selected state, and which toggles its selected state when performed.
 * Can be used to represent a menu item with a checkbox, or a toolbar button which keeps its pressed state.
 */
@SuppressWarnings("StaticInheritance")
public abstract class ToggleAction extends AnAction implements Toggleable {
  public ToggleAction(){
  }

  public ToggleAction(@Nullable final String text){
    super(text);
  }

  public ToggleAction(@Nullable final String text, @Nullable final String description, @Nullable final Icon icon){
    super(text, description, icon);
  }

  @Override
  public final void actionPerformed(@NotNull final AnActionEvent e){
    final boolean state = !isSelected(e);
    setSelected(e, state);
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty(SELECTED_PROPERTY, state);
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
  public void update(@NotNull final AnActionEvent e){
    boolean selected = isSelected(e);
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty(SELECTED_PROPERTY, selected);
  }
}
