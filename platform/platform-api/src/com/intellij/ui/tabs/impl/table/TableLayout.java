/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.ui.tabs.impl.TabLayout;

import java.awt.*;
import java.util.ArrayList;

public class TableLayout extends TabLayout {

  private final JBTabsImpl myTabs;

  public TablePassInfo myLastTableLayout;

  public TableLayout(final JBTabsImpl tabs) {
    myTabs = tabs;
  }

  private TablePassInfo computeLayoutTable() {
    final TablePassInfo data = new TablePassInfo(myTabs);

    final Insets insets = myTabs.getLayoutInsets();
    data.toFitRec =
        new Rectangle(insets.left, insets.top, myTabs.getWidth() - insets.left - insets.right, myTabs.getHeight() - insets.top - insets.bottom);
    int eachRow = 0, eachX = data.toFitRec.x;
    TableRow eachTableRow = new TableRow(data);
    data.table.add(eachTableRow);


    data.requiredRows = 1;
    for (TabInfo eachInfo : myTabs.myVisibleInfos) {
      final TabLabel eachLabel = myTabs.myInfo2Label.get(eachInfo);
      final Dimension size = eachLabel.getPreferredSize();
      if (eachX + size.width >= data.toFitRec.getMaxX()) {
        data.requiredRows++;
        eachX = data.toFitRec.x;
      }
      myTabs.layout(eachLabel, eachX, 0, size.width, 1);
      eachX += size.width;
      data.requiredWidth += size.width;
    }


    int selectedRow = -1;
    eachX = data.toFitRec.x;
    data.rowToFitMaxX = (int)data.toFitRec.getMaxX();

    if (data.requiredRows > 1) {
      final int rowFit = insets.left + data.requiredWidth / data.requiredRows;
      for (TabInfo eachInfo : myTabs.myVisibleInfos) {
        final TabLabel eachLabel = myTabs.myInfo2Label.get(eachInfo);
        final Rectangle eachBounds = eachLabel.getBounds();
        if (eachBounds.contains(rowFit, 0)) {
          data.rowToFitMaxX = (int)eachLabel.getBounds().getMaxX();
          break;
        }
      }
    }

    for (TabInfo eachInfo : myTabs.myVisibleInfos) {
      final TabLabel eachLabel = myTabs.myInfo2Label.get(eachInfo);
      final Dimension size = eachLabel.getPreferredSize();
      if (eachX + size.width <= data.rowToFitMaxX) {
        eachTableRow.add(eachInfo);
        if (myTabs.getSelectedInfo() == eachInfo) {
          selectedRow = eachRow;
        }
        eachX += size.width;
      }
      else {
        eachTableRow = new TableRow(data);
        data.table.add(eachTableRow);
        eachRow++;
        eachX = insets.left + size.width;
        eachTableRow.add(eachInfo);
        if (myTabs.getSelectedInfo() == eachInfo) {
          selectedRow = eachRow;
        }
      }
    }

    java.util.List<TableRow> toMove = new ArrayList<TableRow>();
    for (int i = selectedRow + 1; i < data.table.size(); i++) {
      toMove.add(data.table.get(i));
    }

    for (TableRow eachMove : toMove) {
      data.table.remove(eachMove);
      data.table.add(0, eachMove);
    }


    return data;
  }


  public LayoutPassInfo layoutTable() {
    myTabs.resetLayout(true);
    final TablePassInfo data = computeLayoutTable();
    final Insets insets = myTabs.getLayoutInsets();
    int eachY = insets.top;
    int eachX;
    for (TableRow eachRow : data.table) {
      eachX = insets.left;

      int deltaToFit = 0;
      boolean toAjust = false;
      if (eachRow.width < data.toFitRec.width && data.table.size() > 1) {
        deltaToFit = (int)Math.floor((double)(data.toFitRec.width - eachRow.width) / (double)eachRow.myColumns.size());
        toAjust = true;
      }

      toAjust |= myTabs.myForcedRelayout;

      for (int i = 0; i < eachRow.myColumns.size(); i++) {
        TabInfo tabInfo = eachRow.myColumns.get(i);
        final TabLabel label = myTabs.myInfo2Label.get(tabInfo);

        label.putClientProperty(JBTabsImpl.STRETCHED_BY_WIDTH, Boolean.valueOf(toAjust));
        
        int width;
        if (i < eachRow.myColumns.size() - 1 || !toAjust) {
          width = label.getPreferredSize().width + deltaToFit;
        }
        else {
          width = data.toFitRec.width + insets.left - eachX - 1;
        }


        myTabs.layout(label, eachX, eachY, width, myTabs.myHeaderFitSize.height);
        label.setAligmentToCenter(deltaToFit > 0);

        eachX += width;
      }
      eachY += myTabs.myHeaderFitSize.height - 1;
    }

    if (myTabs.getSelectedInfo() != null) {
      final JBTabsImpl.Toolbar selectedToolbar = myTabs.myInfo2Toolbar.get(myTabs.getSelectedInfo());

      int xAddin = 0;
      if (!myTabs.myHorizontalSide && selectedToolbar != null && !selectedToolbar.isEmpty()) {
        xAddin = selectedToolbar.getPreferredSize().width + 1;
        myTabs.layout(selectedToolbar, insets.left + 1, eachY + 1, selectedToolbar.getPreferredSize().width, myTabs.getHeight() - eachY - insets.bottom - 2);
      }

      myTabs.layoutComp(xAddin, eachY + 2 - myTabs.getLayoutInsets().top, myTabs.getSelectedInfo().getComponent(), 0, 0);
    }

    myLastTableLayout = data;
    return data;
  }

}
