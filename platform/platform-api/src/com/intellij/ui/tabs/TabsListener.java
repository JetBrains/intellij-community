// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import org.jetbrains.annotations.NotNull;

public interface TabsListener {
  default void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
  }

  default void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection) {
  }

  default void tabRemoved(@NotNull TabInfo tabToRemove) {
  }

  default void tabsMoved() {
  }

  /**
   * @deprecated use {@link TabsListener} directly
   */
  @Deprecated
  class Adapter implements TabsListener {
  }
}
