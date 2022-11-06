// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.NlsActions.ActionDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NlsActions.ActionText;

/**
 * An action which has a selected state, and which toggles its selected state when performed.
 * Can be used to represent a menu item with a checkbox, or a toolbar button which keeps its pressed state.
 */
public abstract class ToggleAction extends AnAction implements Toggleable {
  public ToggleAction() {
  }

  public ToggleAction(@Nullable @ActionText final String text) {
    super(() -> text);
  }

  public ToggleAction(@NotNull Supplier<@ActionText String> text) {
    super(text);
  }

  public ToggleAction(@Nullable @ActionText final String text,
                      @Nullable @ActionDescription final String description,
                      @Nullable final Icon icon) {
    super(text, description, icon);
  }

  public ToggleAction(@NotNull Supplier<@ActionText String> text,
                      @NotNull Supplier<@ActionDescription String> description,
                      @Nullable final Icon icon) {
    super(text, description, icon);
  }

  public ToggleAction(@NotNull Supplier<@ActionText String> text, @Nullable final Icon icon) {
    super(text, Presentation.NULL_STRING, icon);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final boolean state = !isSelected(e);
    setSelected(e, state);
    final Presentation presentation = e.getPresentation();
    Toggleable.setSelected(presentation, state);
  }

  /**
   * Returns the selected (checked, pressed) state of the action.
   *
   * @param e the action event representing the place and context in which the selected state is queried.
   * @return true if the action is selected, false otherwise
   */
  public abstract boolean isSelected(@NotNull AnActionEvent e);

  /**
   * Sets the selected state of the action to the specified value.
   *
   * @param e     the action event which caused the state change.
   * @param state the new selected state of the action.
   */
  public abstract void setSelected(@NotNull AnActionEvent e, boolean state);

  @Override
  public void update(@NotNull final AnActionEvent e) {
    boolean selected = isSelected(e);
    final Presentation presentation = e.getPresentation();
    Toggleable.setSelected(presentation, selected);
    if (e.isFromContextMenu()) {
      //force showing check marks instead of toggle icons in the context menu
      presentation.setIcon(null);
    }
  }
}
