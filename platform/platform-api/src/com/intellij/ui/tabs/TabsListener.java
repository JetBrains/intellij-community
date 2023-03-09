// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TabsListener {
  default void selectionChanged(@Nullable TabInfo oldSelection, @Nullable TabInfo newSelection) {
  }

  default void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection) {
  }

  default void tabRemoved(@NotNull TabInfo tabToRemove) {
  }

  default void tabsMoved() {
  }
}
