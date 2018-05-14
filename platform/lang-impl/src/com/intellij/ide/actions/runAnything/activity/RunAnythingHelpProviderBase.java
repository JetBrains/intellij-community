// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.items.RunAnythingHelpItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface RunAnythingHelpProviderBase<V> extends RunAnythingActivityProvider<V> {
  @Nullable
  @Override
  default RunAnythingHelpItem getHelpItem(@NotNull DataContext dataContext) {
    return new RunAnythingHelpItem(getHelpCommandPlaceholder(), getCommandPrefix(), getHelpDescription(), getHelpIcon());
  }

  @Nullable
  default Icon getHelpIcon() {
    return EmptyIcon.ICON_16;
  }

  @Nullable
  default String getHelpDescription() {
    return null;
  }

  @NotNull
  default String getHelpCommandPlaceholder() {
    return getCommandPrefix();
  }

  @NotNull
  String getCommandPrefix();
}