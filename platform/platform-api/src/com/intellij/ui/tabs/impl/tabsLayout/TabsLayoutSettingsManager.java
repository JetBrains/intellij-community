// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.tabsLayout;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public interface TabsLayoutSettingsManager {

  static TabsLayoutSettingsManager getInstance() {
    return ApplicationManager.getApplication().getService(TabsLayoutSettingsManager.class);
  }

  @NotNull
  TabsLayoutInfo getDefaultTabsLayoutInfo();

  @NotNull
  TabsLayoutInfo getSelectedTabsLayoutInfo();
}
