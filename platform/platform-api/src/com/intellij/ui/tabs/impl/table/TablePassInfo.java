// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.table;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TablePassInfo extends LayoutPassInfo {
  final List<TableRow> table = new ArrayList<>();
  public Rectangle toFitRec;
  final Map<TabInfo, TableRow> myInfo2Row = new HashMap<>();

  int requiredWidth;
  int requiredRows;
  int rowToFitMaxX;

  final JBTabsImpl myTabs;

  TablePassInfo(final JBTabsImpl tabs, List<TabInfo> visibleInfos) {
    super(visibleInfos);
    myTabs = tabs;
  }

  @Override
  @Nullable
  public TabInfo getPreviousFor(final TabInfo info) {
    final TableRow row = myInfo2Row.get(info);
    return row != null ? getPrevious(row.myColumns, row.myColumns.indexOf(info)) : null;
  }

  @Override
  @Nullable
  public TabInfo getNextFor(final TabInfo info) {
    final TableRow row = myInfo2Row.get(info);
    return row != null ? getNext(row.myColumns, row.myColumns.indexOf(info)) : null;
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
  public int getColumnCount(final int row) {
    return table.get(row).myColumns.size();
  }

  @Override
  public TabInfo getTabAt(final int row, final int column) {
    return table.get(row).myColumns.get(column);
  }

  @Override
  public boolean hasCurveSpaceFor(final TabInfo tabInfo) {
    final TableRow row = myInfo2Row.get(tabInfo);
    return row != null ? row.myColumns.indexOf(tabInfo) < row.myColumns.size() - 1 : false;
  }

  @Override
  public Rectangle getHeaderRectangle() {
    return (Rectangle)toFitRec.clone();
  }
}
