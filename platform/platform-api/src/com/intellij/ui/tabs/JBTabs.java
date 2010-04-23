/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.tabs;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Getter;
import com.intellij.ui.switcher.SwitchProvider;
import com.intellij.ui.switcher.SwitchTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.Comparator;

public interface JBTabs extends SwitchProvider {

  @NotNull
  TabInfo addTab(TabInfo info, int index);

  @NotNull
  TabInfo addTab(TabInfo info);

  ActionCallback removeTab(@Nullable TabInfo info);

  ActionCallback removeTab(@Nullable TabInfo info, @Nullable TabInfo forcedSelectionTranfer);

  ActionCallback removeTab(@Nullable TabInfo info, @Nullable TabInfo forcedSelectionTranfer, boolean transferFocus);

  void removeAllTabs();

  @Nullable
  ActionGroup getPopupGroup();

  @Nullable
  String getPopupPlace();

  JBTabs setPopupGroup(@NotNull ActionGroup popupGroup, @NotNull String place, final boolean addNavigationGroup);
  JBTabs setPopupGroup(@NotNull Getter<ActionGroup> popupGroup, @NotNull String place, final boolean addNavigationGroup);
  
  ActionCallback select(@NotNull TabInfo info, boolean requestFocus);

  @Nullable
  TabInfo getSelectedInfo();

  @NotNull
  TabInfo getTabAt(int tabIndex);

  @NotNull
  List<TabInfo> getTabs();

  int getTabCount();

  @NotNull
  JBTabsPresentation getPresentation();

  @Nullable
  DataProvider getDataProvider();

  JBTabs setDataProvider(@NotNull DataProvider dataProvider);

  @Nullable
  TabInfo getTargetInfo();

  @NotNull
  JBTabs addTabMouseListener(@NotNull MouseListener listener);

  @NotNull
  JBTabs removeTabMouseListener(@NotNull MouseListener listener);

  JBTabs addListener(@NotNull TabsListener listener);

  JBTabs removeListener(@NotNull TabsListener listener);

  @NotNull
  JComponent getComponent();

  void updateTabActions(boolean validateNow);

  @Nullable
  TabInfo findInfo(Component component);

  @Nullable
  TabInfo findInfo(MouseEvent event);

  @Nullable
  TabInfo findInfo(Object object);

  int getIndexOf(@Nullable final TabInfo tabInfo);

  void sortTabs(Comparator<TabInfo> comparator);

  void requestFocus();

  JBTabs setNavigationActiondBinding(String prevActiobId, String nextActionId);
  JBTabs setNavigationActionsEnabled(boolean enabled);

  boolean isDisposed();

  JBTabs setAdditinalSwitchProviderWhenOriginal(SwitchProvider delegate);
}
