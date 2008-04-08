package com.intellij.ui.tabs.impl.table;

import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.Layout;
import com.intellij.ui.tabs.impl.TabLabel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class TableLayout {

  private JBTabsImpl myTabs;

  public TableLayoutData myLastTableLayout;

  public TableLayout(final JBTabsImpl tabs) {
    myTabs = tabs;
  }

  private TableLayoutData computeLayoutTable() {
    final TableLayoutData data = new TableLayoutData(myTabs);

    final Insets insets = myTabs.getLayoutInsets();
    data.toFitRec =
        new Rectangle(insets.left, insets.top, myTabs.getWidth() - insets.left - insets.right, myTabs.getHeight() - insets.top - insets.bottom);
    int eachRow = 0, eachX = data.toFitRec.x;
    TableRow eachTableRow = new TableRow(data);
    data.table.add(eachTableRow);

    int selectedRow = -1;
    for (TabInfo eachInfo : myTabs.myVisibleInfos) {
      final TabLabel eachLabel = myTabs.myInfo2Label.get(eachInfo);
      final Dimension size = eachLabel.getPreferredSize();
      if (eachX + size.width <= data.toFitRec.getMaxX()) {
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
        eachX = insets.left;
        eachTableRow.add(eachInfo);
        if (myTabs.getSelectedInfo() == eachInfo) {
          selectedRow = eachRow;
        }
        if (eachX + size.width <= data.toFitRec.getMaxX()) {
          eachX += size.width;
        }
        else {
          eachTableRow = new TableRow(data);
          eachRow++;
          eachX = insets.left;
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


  public  Layout layoutTable() {
    myTabs.resetLayout(true);
    final TableLayoutData data = computeLayoutTable();
    final Insets insets = myTabs.getLayoutInsets();
    int eachY = insets.top;
    int eachX;
    for (TableRow eachRow : data.table) {
      eachX = insets.left;

      int deltaToFit = 0;
      if (eachRow.width < data.toFitRec.width && data.table.size() > 1) {
        deltaToFit = (int)Math.floor((double)(data.toFitRec.width - eachRow.width) / (double)eachRow.myColumns.size());
      }

      for (int i = 0; i < eachRow.myColumns.size(); i++) {
        TabInfo tabInfo = eachRow.myColumns.get(i);
        final TabLabel label = myTabs.myInfo2Label.get(tabInfo);

        int width;
        if (i < eachRow.myColumns.size() - 1 || deltaToFit == 0) {
          width = label.getPreferredSize().width + deltaToFit;
        }
        else {
          width = data.toFitRec.width + insets.left - eachX - 1;
        }


        label.setBounds(eachX, eachY, width, myTabs.myHeaderFitSize.height);
        label.setAligmentToCenter(deltaToFit > 0);

        eachX += width;
      }
      eachY += myTabs.myHeaderFitSize.height - 1;
    }

    if (myTabs.getSelectedInfo() != null) {
      final JComponent selectedToolbar = myTabs.myInfo2Toolbar.get(myTabs.getSelectedInfo());

      int xAddin = 0;
      if (!myTabs.myHorizontalSide && selectedToolbar != null) {
        xAddin = selectedToolbar.getPreferredSize().width + 1;
        selectedToolbar
            .setBounds(insets.left + 1, eachY + 1, selectedToolbar.getPreferredSize().width, myTabs.getHeight() - eachY - insets.bottom - 2);
      }

      myTabs.layoutComp(xAddin, eachY + 3, myTabs.getSelectedInfo().getComponent());
    }

    myLastTableLayout = data;
    return data;
  }

}
