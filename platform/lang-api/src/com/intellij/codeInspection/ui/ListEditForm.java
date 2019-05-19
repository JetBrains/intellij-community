/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.List;

public class ListEditForm {
  JPanel contentPanel;
  ListTable table;

  public ListEditForm(String title, List<String> stringList) {
    table = new ListTable(new ListWrappingTableModel(stringList, title));

    contentPanel = ToolbarDecorator.createDecorator(table)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final ListWrappingTableModel tableModel = table.getModel();
          tableModel.addRow();
          EventQueue.invokeLater(() -> {
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
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(component, true));
          });
        }
      })
      .setRemoveAction(button -> TableUtil.removeSelectedItems(table))
      .disableUpDownActions().createPanel();
  }

  public JComponent getContentPanel() {
    return contentPanel;
  }
}
