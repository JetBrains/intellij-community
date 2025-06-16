// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.NlsActions.ActionDescription;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NlsActions.ActionText;

/**
 * An action which has a "selected" state and which toggles it when performed.
 * Can be used to represent a menu item with a checkbox, or a toolbar button which keeps its pressed state.
 * <p>
 * Toggle actions are {@link KeepPopupOnPerform#IfPreferred} by default.
 * Make it {@link KeepPopupOnPerform#IfRequested} if you want the toggle to keep its popup open only when
 * the user explicitly requests that.
 */
public abstract class ToggleAction extends AnAction implements Toggleable {
  public ToggleAction() {
  }

  public ToggleAction(@Nullable @ActionText String text) {
    super(() -> text);
  }

  public ToggleAction(@NotNull Supplier<@ActionText String> text) {
    super(text);
  }

  public ToggleAction(@Nullable @ActionText String text,
                      @Nullable @ActionDescription String description,
                      @Nullable Icon icon) {
    super(text, description, icon);
  }

  public ToggleAction(@NotNull Supplier<@ActionText String> text,
                      @NotNull Supplier<@ActionDescription String> description,
                      @Nullable Icon icon) {
    super(text, description, icon);
  }

  public ToggleAction(@NotNull Supplier<@ActionText String> text, @Nullable Icon icon) {
    super(text, Presentation.NULL_STRING, icon);
  }

  @Override
  @NotNull
  @ApiStatus.Internal
  public Presentation createTemplatePresentation() {
    Presentation presentation = super.createTemplatePresentation();
    presentation.setKeepPopupOnPerform(KeepPopupOnPerform.IfPreferred);
    return presentation;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    boolean state = !isSelected(e);
    setSelected(e, state);
    Presentation presentation = e.getPresentation();
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
  public void update(@NotNull AnActionEvent e) {
    boolean selected = isSelected(e);
    Presentation presentation = e.getPresentation();
    Toggleable.setSelected(presentation, selected);
    if (e.getUiKind() instanceof ActionUiKind.Popup o && !o.isSearchPopup()) {
      // force showing check marks instead of toggle icons
      presentation.setIcon(null);
    }
  }
}
