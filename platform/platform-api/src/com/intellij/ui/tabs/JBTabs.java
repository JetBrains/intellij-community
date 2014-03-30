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
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.switcher.SwitchProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public interface JBTabs extends SwitchProvider {

  @NotNull
  TabInfo addTab(TabInfo info, int index);

  @NotNull
  TabInfo addTab(TabInfo info);

  @NotNull
  ActionCallback removeTab(@Nullable TabInfo info);

  void removeAllTabs();

  @NotNull
  JBTabs setPopupGroup(@NotNull ActionGroup popupGroup, @NotNull String place, final boolean addNavigationGroup);

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

  @Nullable
  TabInfo getTargetInfo();

  @NotNull
  JBTabs addTabMouseListener(@NotNull MouseListener listener);

  JBTabs addListener(@NotNull TabsListener listener);

  JBTabs setSelectionChangeHandler(SelectionChangeHandler handler);

  @Override
  @NotNull
  JComponent getComponent();

  @Nullable
  TabInfo findInfo(MouseEvent event);

  @Nullable
  TabInfo findInfo(Object object);

  int getIndexOf(@Nullable final TabInfo tabInfo);

  void requestFocus();

  JBTabs setNavigationActionBinding(String prevActiobId, String nextActionId);
  JBTabs setNavigationActionsEnabled(boolean enabled);

  boolean isDisposed();

  JBTabs setAdditionalSwitchProviderWhenOriginal(SwitchProvider delegate);

  void resetDropOver(TabInfo tabInfo);
  Image startDropOver(TabInfo tabInfo, RelativePoint point);
  void processDropOver(TabInfo over, RelativePoint point);

  interface SelectionChangeHandler {
    @NotNull
    ActionCallback execute(final TabInfo info, final boolean requestFocus, @NotNull ActiveRunnable doChangeSelection);
  }
}
