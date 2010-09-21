/*
 * Copyright 2007-2010 Bas Leijdekkers
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
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;

public class AddAction extends AbstractAction {

  final ListTable table;

  public AddAction(ListTable table) {
    this.table = table;
    putValue(NAME, CommonBundle.message("button.add.a"));
  }

  public void actionPerformed(ActionEvent e) {
    final ListWrappingTableModel tableModel = table.getModel();
    tableModel.addRow();
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        final int lastRowIndex = tableModel.getRowCount() - 1;
        final Rectangle rectangle =
          table.getCellRect(lastRowIndex, 0, true);
        table.scrollRectToVisible(rectangle);
        table.editCellAt(lastRowIndex, 0);
        final ListSelectionModel selectionModel =
          table.getSelectionModel();
        selectionModel.setSelectionInterval(lastRowIndex, lastRowIndex);
        final TableCellEditor editor = table.getCellEditor();
        final Component component =
          editor.getTableCellEditorComponent(table,
                                             null, true, lastRowIndex, 0);
        component.requestFocus();
      }
    });
  }
}