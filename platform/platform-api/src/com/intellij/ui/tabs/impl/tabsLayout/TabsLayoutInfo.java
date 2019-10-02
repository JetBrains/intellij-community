// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.tabsLayout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TabsLayoutInfo {
  //private static final Logger LOG = Logger.getInstance(TabsLayoutInfo.class);

  @NotNull
  abstract public String getId();

  @NotNull
  abstract public String getName();

  @NotNull
  final public TabsLayout createTabsLayout(@NotNull TabsLayoutCallback callback) {
    TabsLayout layout = createTabsLayoutInstance();
    layout.init(callback);
    return layout;
  }

  @NotNull
  abstract protected TabsLayout createTabsLayoutInstance();

  public String toString() {
    return getClass().getName();
  }

  @Nullable
  public Integer[] getAvailableTabsPositions() {
    return null;
  }
}
