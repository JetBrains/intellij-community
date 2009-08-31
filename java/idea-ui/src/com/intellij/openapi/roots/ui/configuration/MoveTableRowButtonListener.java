/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ui.TableUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: May 21, 2004
 */
class MoveTableRowButtonListener implements ActionListener {
  private final int myDelta;
  private final JTable myTable;
  private final Runnable myOnRowMoveAction;

  public MoveTableRowButtonListener(int delta, JTable table, Runnable onRowMoveAction) {
    myDelta = delta;
    myTable = table;
    myOnRowMoveAction = onRowMoveAction;
  }

  public void actionPerformed(ActionEvent e) {
    final int moveCount = Math.abs(myDelta);
    for (int idx = 0; idx < moveCount; idx++) {
      if (myDelta < 0) {
        TableUtil.moveSelectedItemsUp(myTable);
      }
      else {
        TableUtil.moveSelectedItemsDown(myTable);
      }
    }
    /*
    final int selectedIndex = myTable.getSelectedRow();
    if (selectedIndex < 0) {
      return;
    }
    final DefaultTableModel tableModel = (DefaultTableModel)myTable.getModel();
    final int nextIndex = calcNextIndex(selectedIndex, tableModel.getRowCount(), myDelta);
    if (selectedIndex == nextIndex) {
      return;
    }
    tableModel.moveRow(selectedIndex, selectedIndex, nextIndex);
    myTable.getSelectionModel().setSelectionInterval(nextIndex, nextIndex);
    */
    if (myOnRowMoveAction != null) {
      myOnRowMoveAction.run();
    }
    myTable.requestFocus();
  }
}
