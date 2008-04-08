package com.intellij.ui.tabs.impl.table;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.LayoutPassInfo;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TablePassInfo extends LayoutPassInfo {
    List<TableRow> table = new ArrayList<TableRow>();
    public Rectangle toFitRec;
    Map<TabInfo, TableRow> myInfo2Row = new HashMap<TabInfo, TableRow>();

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
  }
