// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface RunAnythingHelpProvider<V> extends RunAnythingActivityProvider<V> {
  @NotNull
  RunAnythingItem getHelpItem(@NotNull DataContext dataContext);

  @NotNull
  String getHelpCommandPlaceholder();

  @Nullable
  default String getHelpDescription() {
    return null;
  }

  @Nullable
  default Icon getIcon() {
    return EmptyIcon.ICON_16;
  }
}