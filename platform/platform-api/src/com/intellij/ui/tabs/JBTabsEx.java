// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Producer;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Comparator;


public interface JBTabsEx extends JBTabs {
  DataKey<JBTabsEx> NAVIGATION_ACTIONS_KEY = DataKey.create("JBTabs");

  boolean isEditorTabs();

  void updateTabActions(boolean validateNow);

  TabInfo addTabSilently(TabInfo info, int index);

  void removeTab(TabInfo info, @Nullable TabInfo forcedSelectionTransfer);

  @Nullable
  TabInfo getToSelectOnRemoveOf(TabInfo info);

  void sortTabs(@NotNull Comparator<? super TabInfo> comparator);

  int getDropInfoIndex();

  @MagicConstant(intValues = {SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT, -1})
  int getDropSide();

  boolean isEmptyVisible();

  void setTitleProducer(@Nullable Producer<? extends Pair<Icon, String>> titleProducer);

  void setHideTopPanel(boolean isHideTopPanel);

  boolean isHideTopPanel();
}
