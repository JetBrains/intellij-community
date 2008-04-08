package com.intellij.ui.tabs.impl.table;

import com.intellij.ui.tabs.TabInfo;

import java.util.List;
import java.util.ArrayList;

class TableRow {

  TableLayoutData myData;
  List<TabInfo> myColumns = new ArrayList<TabInfo>();
  int width;

  public TableRow(final TableLayoutData data) {
    myData = data;
  }

  void add(TabInfo info) {
    myColumns.add(info);
    width += myData.myTabs.myInfo2Label.get(info).getPreferredSize().width;
    myData.myInfo2Row.put(info, this);
  }

}
