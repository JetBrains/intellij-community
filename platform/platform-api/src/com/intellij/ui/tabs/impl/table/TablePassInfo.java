// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.table;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.*;

public class TablePassInfo extends LayoutPassInfo {
  final List<TableRow> table = new ArrayList<>();
  public final Rectangle toFitRec;
  public final Rectangle tabRectangle = new Rectangle();
  final Map<TabInfo, TableRow> myInfo2Row = new HashMap<>();
  final JBTabsImpl myTabs;
  @NotNull public final Rectangle entryPointRect = new Rectangle();
  @NotNull public final Rectangle moreRect = new Rectangle();
  @NotNull public final Rectangle titleRect = new Rectangle();
  public final List<TabInfo> invisible = new ArrayList<>();
  final Map<TabInfo, Integer> lengths = new LinkedHashMap<>();
  final Map<TabInfo, Rectangle> bounds = new HashMap<>();
  int requiredLength = 0;

  TablePassInfo(TableLayout layout, List<TabInfo> visibleInfos) {
    super(visibleInfos);
    myTabs = layout.myTabs;
    final Insets insets = myTabs.getLayoutInsets();
    toFitRec =
      new Rectangle(insets.left, insets.top, myTabs.getWidth() - insets.left - insets.right, myTabs.getHeight() - insets.top - insets.bottom);
  }

  public boolean isInSelectionRow(final TabInfo tabInfo) {
    final TableRow row = myInfo2Row.get(tabInfo);
    final int index = table.indexOf(row);
    return index != -1 && index == table.size() - 1;
  }

  @Override
  public int getRowCount() {
    return table.size();
  }

  @Override
  public Rectangle getHeaderRectangle() {
    return (Rectangle)toFitRec.clone();
  }

  @Override
  public int getRequiredLength() {
    return requiredLength;
  }
}
