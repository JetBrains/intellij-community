// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

/** @deprecated Use regular {@link com.intellij.openapi.actionSystem.ToggleAction} instead */
@Deprecated(forRemoval = true)
public abstract class ToggleActionButton extends AnActionButton implements Toggleable {
  public ToggleActionButton(@NlsActions.ActionText String text, Icon icon) {
    super(() -> text, Presentation.NULL_STRING, icon);
  }

  public ToggleActionButton(@NotNull Supplier<String> text, Icon icon) {
    super(text, Presentation.NULL_STRING, icon);
  }

  /**
   * Returns the selected (checked, pressed) state of the action.
   *
   * @param e the action event representing the place and context in which the selected state is queried.
   * @return true if the action is selected, false otherwise
   */
  public abstract boolean isSelected(AnActionEvent e);

  /**
   * Sets the selected state of the action to the specified value.
   *
   * @param e     the action event which caused the state change.
   * @param state the new selected state of the action.
   */
  public abstract void setSelected(AnActionEvent e, boolean state);

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    boolean state = !isSelected(e);
    setSelected(e, state);
    Presentation presentation = e.getPresentation();
    Toggleable.setSelected(presentation, state);
  }

  @Override
  public final void updateButton(@NotNull AnActionEvent e) {
    boolean selected = isSelected(e);
    Toggleable.setSelected(e.getPresentation(), selected);
  }
}
