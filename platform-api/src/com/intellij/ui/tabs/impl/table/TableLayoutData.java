package com.intellij.ui.tabs.impl.table;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.Layout;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TableLayoutData extends Layout {
    List<TableRow> table = new ArrayList<TableRow>();
    public Rectangle toFitRec;
    Map<TabInfo, TableRow> myInfo2Row = new HashMap<TabInfo, TableRow>();

  JBTabsImpl myTabs;

  TableLayoutData(final JBTabsImpl tabs) {
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
