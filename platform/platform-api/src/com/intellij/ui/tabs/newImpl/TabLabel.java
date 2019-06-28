// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl;

import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.ApiStatus;

@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
public class TabLabel extends com.intellij.ui.tabs.impl.TabLabel {
  public TabLabel(JBTabsImpl tabs, TabInfo info) {
    super(tabs, info);
  }
}
