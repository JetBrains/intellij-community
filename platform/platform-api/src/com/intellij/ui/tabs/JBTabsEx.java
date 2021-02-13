// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo;
import com.intellij.util.Producer;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;

/**
 * @author yole
 */
public interface JBTabsEx extends JBTabs {
  DataKey<JBTabsEx> NAVIGATION_ACTIONS_KEY = DataKey.create("JBTabs");

  boolean isEditorTabs();

  void updateTabActions(boolean validateNow);

  TabInfo addTabSilently(TabInfo info, int index);

  @NotNull
  ActionCallback removeTab(TabInfo info, @Nullable TabInfo forcedSelectionTransfer, boolean transferFocus);

  @Nullable
  TabInfo getToSelectOnRemoveOf(TabInfo info);

  void sortTabs(Comparator<? super TabInfo> comparator);

  int getDropInfoIndex();

  @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT, -1})
  int getDropSide();

  boolean isEmptyVisible();

  void updateTabsLayout(@NotNull TabsLayoutInfo newTabsLayoutInfo);

  void setTitleProducer(@Nullable Producer<Pair<Icon, String>> titleProducer);
}
