/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

  @Nullable
  public TabInfo getPreviousFor(final TabInfo info) {
    final TableRow row = myInfo2Row.get(info);
    return row != null ? getPrevious(row.myColumns, row.myColumns.indexOf(info)) : null;
  }

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

  public int getRowCount() {
    return table.size();
  }

  public int getColumnCount(final int row) {
    return table.get(row).myColumns.size();
  }

  public TabInfo getTabAt(final int row, final int column) {
    return table.get(row).myColumns.get(column);
  }

  public boolean hasCurveSpaceFor(final TabInfo tabInfo) {
    final TableRow row = myInfo2Row.get(tabInfo);
    return row != null ? row.myColumns.indexOf(tabInfo) < row.myColumns.size() - 1 : false;
  }

  @Override
  public Rectangle getHeaderRectangle() {
    return (Rectangle)toFitRec.clone();
  }
}
