// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.openapi.util.Getter;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;

public interface JBTabs {
  @NotNull
  TabInfo addTab(TabInfo info, int index);

  @NotNull
  TabInfo addTab(TabInfo info);

  @NotNull
  ActionCallback removeTab(@Nullable TabInfo info);
  void removeAllTabs();

  @NotNull
  ActionCallback select(@NotNull TabInfo info, boolean requestFocus);

  @Nullable
  TabInfo getSelectedInfo();

  @NotNull
  TabInfo getTabAt(int tabIndex);

  int getTabCount();

  @NotNull
  JBTabsPresentation getPresentation();

  @Nullable
  DataProvider getDataProvider();

  JBTabs setDataProvider(@NotNull DataProvider dataProvider);

  @NotNull
  List<TabInfo> getTabs();

  @Nullable
  TabInfo getTargetInfo();

  @NotNull
  JBTabs addTabMouseListener(@NotNull MouseListener listener);

  JBTabs addListener(@NotNull TabsListener listener);

  JBTabs setSelectionChangeHandler(SelectionChangeHandler handler);

  @NotNull
  JComponent getComponent();

  @Nullable
  TabInfo findInfo(MouseEvent event);

  @Nullable
  TabInfo findInfo(Object object);

  @Nullable
  TabInfo findInfo(Component component);

  int getIndexOf(@Nullable final TabInfo tabInfo);

  void requestFocus();

  JBTabs setNavigationActionBinding(String prevActiobId, String nextActionId);
  JBTabs setNavigationActionsEnabled(boolean enabled);

  @NotNull
  JBTabs setPopupGroup(@NotNull ActionGroup popupGroup, @NotNull String place, boolean addNavigationGroup);

  @NotNull
  JBTabs setPopupGroup(@NotNull Getter<? extends ActionGroup> popupGroup,
                       @NotNull String place,
                       boolean addNavigationGroup);

  void resetDropOver(TabInfo tabInfo);
  Image startDropOver(TabInfo tabInfo, RelativePoint point);
  void processDropOver(TabInfo over, RelativePoint point);

  Component getTabLabel(TabInfo tabInfo);

  interface SelectionChangeHandler {
    @NotNull
    ActionCallback execute(final TabInfo info, final boolean requestFocus, @NotNull ActiveRunnable doChangeSelection);
  }
}
