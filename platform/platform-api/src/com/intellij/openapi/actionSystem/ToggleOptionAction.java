// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.NlsActions.ActionDescription;
import com.intellij.openapi.util.NlsActions.ActionText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public class ToggleOptionAction extends ToggleAction {
  private final @NotNull Function<? super AnActionEvent, ? extends Option> optionSupplier;

  @SuppressWarnings("unused")
  public ToggleOptionAction(@NotNull Option option) {
    super();
    this.optionSupplier = event -> option;
  }

  @SuppressWarnings("unused")
  public ToggleOptionAction(@NotNull Option option, @Nullable Icon icon) {
    this(event -> option, icon);
  }

  @SuppressWarnings("unused")
  public ToggleOptionAction(@NotNull Function<? super AnActionEvent, ? extends Option> optionSupplier) {
    super();
    this.optionSupplier = optionSupplier;
  }

  public ToggleOptionAction(@NotNull Function<? super AnActionEvent, ? extends Option> optionSupplier, @Nullable Icon icon) {
    super(() -> null, () -> null, icon);
    this.optionSupplier = optionSupplier;
  }

  @Override
  public final boolean isSelected(@NotNull AnActionEvent event) {
    Option option = optionSupplier.apply(event);
    return option != null && option.isSelected();
  }

  @Override
  public final void setSelected(@NotNull AnActionEvent event, boolean selected) {
    Option option = optionSupplier.apply(event);
    if (option != null) option.setSelected(selected);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Option option = optionSupplier.apply(event);
    boolean enabled = option != null && option.isEnabled();
    boolean visible = enabled || option != null && option.isAlwaysVisible();
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(enabled);
    presentation.setVisible(visible);
    if (visible) {
      Toggleable.setSelected(presentation, option.isSelected());
      String name = option.getName();
      if (name != null) presentation.setText(name);
      String description = option.getDescription();
      if (description != null) {
        presentation.setDescription(description);
      }
      if (event.isFromContextMenu()) {
        presentation.setIcon(null);
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  public interface Option {
    /**
     * @return a string to override an action name
     */
    default @Nullable @ActionText String getName() {
      return null;
    }

    /**
     * @return a string to override an action description
     */
    default @Nullable @ActionDescription String getDescription() {
      return null;
    }

    default boolean isEnabled() {
      return true;
    }

    default boolean isAlwaysVisible() {
      return false;
    }

    boolean isSelected();

    void setSelected(boolean selected);
  }
}
