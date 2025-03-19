// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything;

import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Internal
public final class RunAnythingRunConfigurationItem extends RunAnythingItemBase {
  private final ChooseRunConfigurationPopup.ItemWrapper myWrapper;

  public RunAnythingRunConfigurationItem(@NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper, @Nullable Icon icon) {
    super(wrapper.getText(), icon);
    myWrapper = wrapper;
  }

  @Override
  public @Nullable String getDescription() {
    ConfigurationType type = myWrapper.getType();
    return type == null ? null : type.getConfigurationTypeDescription();
  }
}