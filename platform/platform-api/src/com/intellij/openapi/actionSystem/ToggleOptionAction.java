// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    this(option, null);
  }

  @SuppressWarnings("unused")
  public ToggleOptionAction(@NotNull Option option, @Nullable Icon icon) {
    this(event -> option, icon);
  }

  @SuppressWarnings("unused")
  public ToggleOptionAction(@NotNull Function<? super AnActionEvent, ? extends Option> optionSupplier) {
    this(optionSupplier, null);
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
  public final void update(@NotNull AnActionEvent event) {
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
      if (description != null) presentation.setDescription(description);
      if (ActionPlaces.isPopupPlace(event.getPlace())) presentation.setIcon(null);
    }
  }

  public interface Option {
    /**
     * @return a not null string to override an action name
     */
    @Nullable
    @ActionText
    default String getName() {
      return null;
    }

    /**
     * @return a not null string to override an action description
     */
    @Nullable
    @ActionDescription
    default String getDescription() {
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
