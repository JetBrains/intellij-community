// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.tabsLayout;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public interface TabsLayoutCallback {
  TabLabel getTabLabel(TabInfo info);

  TabInfo getSelectedInfo();

  JBTabsImpl.Toolbar getToolbar(TabInfo tabInfo);

  boolean isHorizontalToolbar();

  boolean isHiddenTabs();

  List<TabInfo> getVisibleTabsInfos();

  Map<TabInfo, Integer> getHiddenInfos();

  WeakHashMap<Component, Component> getDeferredToRemove();

  int getAllTabsCount();

  Insets getLayoutInsets();

  Insets getInnerInsets();

  int getFirstTabOffset();

  boolean isEditorTabs();

  JBTabsPosition getTabsPosition();

  boolean isDropTarget(TabInfo tabInfo);

  boolean isToolbarOnTabs();

  boolean isToolbarBeforeTabs();

  int getToolbarInsetForOnTabsMode();

  TabInfo getDropInfo();

  boolean isShowDropLocation();

  int getDropInfoIndex();

  ActionCallback selectTab(@NotNull TabInfo info, boolean requestFocus);

  JComponent getComponent();

  void relayout(boolean forced, final boolean layoutNow);

  int tabMSize();

  int getBorderThickness();
}