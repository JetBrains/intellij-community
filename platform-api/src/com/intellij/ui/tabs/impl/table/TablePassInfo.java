package com.intellij.ui.tabs.impl.table;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TablePassInfo extends LayoutPassInfo {
  List<TableRow> table = new ArrayList<TableRow>();
  public Rectangle toFitRec;
  Map<TabInfo, TableRow> myInfo2Row = new HashMap<TabInfo, TableRow>();

  int requiredWidth;
  int requiredRows;
  int rowToFitMaxX;

  JBTabsImpl myTabs;

  TablePassInfo(final JBTabsImpl tabs) {
    myTabs = tabs;
  }

  public TabInfo getPreviousFor(final TabInfo info) {
    final TableRow row = myInfo2Row.get(info);
    return getPrevious(row.myColumns, row.myColumns.indexOf(info));
  }

  public TabInfo getNextFor(final TabInfo info) {
    final TableRow row = myInfo2Row.get(info);
    return getNext(row.myColumns, row.myColumns.indexOf(info));
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
    return row.myColumns.indexOf(tabInfo) < row.myColumns.size() - 1;
  }
}
