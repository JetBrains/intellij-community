/*
 * Copyright 2007 Bas Leijdekkers
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
package com.intellij.codeInspection.ui;

import com.intellij.CommonBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class RemoveAction extends AbstractAction {

  private final ListTable table;

  public RemoveAction(ListTable table) {
    this.table = table;
    putValue(NAME, CommonBundle.message("button.remove.r"));
  }

  public void actionPerformed(ActionEvent e) {
    final ListSelectionModel selectionModel = table.getSelectionModel();
    final int minIndex = selectionModel.getMinSelectionIndex();
    final int maxIndex = selectionModel.getMaxSelectionIndex();
    if (minIndex == -1 || maxIndex == -1) {
      return;
    }
    final ListWrappingTableModel tableModel = table.getModel();
    for (int i = minIndex; i <= maxIndex; i++) {
      if (selectionModel.isSelectedIndex(i)) {
        tableModel.removeRow(i);
      }
    }
    final int count = tableModel.getRowCount();
    if (count <= minIndex) {
      selectionModel.setSelectionInterval(count - 1,
                                          count - 1);
    }
    else if (minIndex <= 0) {
      if (count > 0) {
        selectionModel.setSelectionInterval(0, 0);
      }
    }
    else {
      selectionModel.setSelectionInterval(minIndex - 1,
                                          minIndex - 1);
    }
  }
}