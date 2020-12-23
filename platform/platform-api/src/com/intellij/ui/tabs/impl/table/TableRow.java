// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.table;

import com.intellij.ui.tabs.TabInfo;

import java.util.ArrayList;
import java.util.List;

class TableRow {

  private final TablePassInfo myData;
  final List<TabInfo> myColumns = new ArrayList<>();
  int width;

  TableRow(final TablePassInfo data) {
    myData = data;
  }

  void add(TabInfo info, int width) {
    myColumns.add(info);
    this.width += width;
    myData.myInfo2Row.put(info, this);
  }
}
