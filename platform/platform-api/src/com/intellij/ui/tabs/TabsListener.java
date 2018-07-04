// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

public interface TabsListener {
  default void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
  }

  default void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection) {
  }
  
  default void tabRemoved(TabInfo tabToRemove) {
  }
  
  default void tabsMoved() {
  }

  /**
   * Use {@link TabsListener} directly
   */
  @Deprecated
  class Adapter implements TabsListener {
  }
}
