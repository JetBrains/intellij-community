/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
class TableToolbarDecorator extends ToolbarDecorator {
  private final JTable myTable;
  @Nullable private final ElementProducer<?> myProducer;

  TableToolbarDecorator(@NotNull JTable table, @Nullable final ElementProducer<?> producer) {
    myTable = table;
    myProducer = producer;
    myAddActionEnabled = myRemoveActionEnabled = myUpActionEnabled = myDownActionEnabled = isModelEditable();
    if (isModelEditable()) {
      createDefaultTableActions(producer);
    }
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateButtons();
      }
    });
    myTable.addPropertyChangeListener("enabled", new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        updateButtons();
      }
    });
  }

  @Override
  protected JComponent getComponent() {
    return myTable;
  }

  @Override
  protected void updateButtons() {
    final CommonActionsPanel p = getActionsPanel();
    if (p != null) {
      boolean someElementSelected;
      if (myTable.isEnabled()) {
        final int index = myTable.getSelectedRow();
        final int size = myTable.getModel().getRowCount();
        someElementSelected = 0 <= index && index < size;
        if (someElementSelected) {
          final boolean downEnable = myTable.getSelectionModel().getMaxSelectionIndex() < size - 1;
          final boolean upEnable = myTable.getSelectionModel().getMinSelectionIndex() > 0;
          final boolean editEnabled = myTable.getSelectedRowCount() == 1;
          p.setEnabled(CommonActionsPanel.Buttons.EDIT, editEnabled);
          p.setEnabled(CommonActionsPanel.Buttons.UP, upEnable);
          p.setEnabled(CommonActionsPanel.Buttons.DOWN, downEnable);
        }
        else {
          p.setEnabled(CommonActionsPanel.Buttons.EDIT, false);
          p.setEnabled(CommonActionsPanel.Buttons.UP, false);
          p.setEnabled(CommonActionsPanel.Buttons.DOWN, false);
        }
        p.setEnabled(CommonActionsPanel.Buttons.ADD, myProducer == null || myProducer.canCreateElement());
      }
      else {
        someElementSelected = false;
        p.setEnabled(CommonActionsPanel.Buttons.ADD, false);
        p.setEnabled(CommonActionsPanel.Buttons.EDIT, false);
        p.setEnabled(CommonActionsPanel.Buttons.UP, false);
        p.setEnabled(CommonActionsPanel.Buttons.DOWN, false);
      }

      p.setEnabled(CommonActionsPanel.Buttons.REMOVE, someElementSelected);
      updateExtraElementActions(someElementSelected);
    }
  }

  private void createDefaultTableActions(@Nullable final ElementProducer<?> producer) {
    final JTable table = myTable;
    final EditableModel tableModel = (EditableModel)table.getModel();

    myAddAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.stopEditing(table);
        final int rowCount = table.getRowCount();
        if (tableModel instanceof ListTableModel && producer != null) {
          //noinspection unchecked
          ((ListTableModel)tableModel).addRow(producer.createElement());
        } else {
          tableModel.addRow();
        }
        if (rowCount == table.getRowCount()) return;
        final int index = table.getModel().getRowCount() - 1;

        table.setRowSelectionInterval(index, index);
        table.setColumnSelectionInterval(0, 0);
        table.editCellAt(index, 0);

        TableUtil.updateScroller(table);
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            final Component editorComponent = table.getEditorComponent();
            if (editorComponent != null) {
              final Rectangle bounds = editorComponent.getBounds();
              table.scrollRectToVisible(bounds);
              editorComponent.requestFocus();
            }
          }
        });


      }
    };

    myRemoveAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        if (TableUtil.doRemoveSelectedItems(table, tableModel, null)) {
          updateButtons();
          table.requestFocus();
          TableUtil.updateScroller(table);
        }
      }
    };

    class MoveRunnable implements AnActionButtonRunnable {
      final int delta;

      MoveRunnable(int delta) {
        this.delta = delta;
      }

      @Override
      public void run(AnActionButton button) {
        int row = table.getEditingRow();
        int col = table.getEditingColumn();
        TableUtil.stopEditing(table);
        int[] idx = table.getSelectedRows();
        Arrays.sort(idx);
        if (delta > 0) {
          idx = ArrayUtil.reverseArray(idx);
        }

        if (idx.length == 0) return;
        if (idx[0] + delta < 0) return;
        if (idx[idx.length - 1] + delta > table.getModel().getRowCount()) return;

        for (int i = 0; i < idx.length; i++) {
          tableModel.exchangeRows(idx[i], idx[i] + delta);
          idx[i] += delta;
        }
        TableUtil.selectRows(table, idx);
        table.requestFocus();
        if (row > 0 && col != -1) {
          table.editCellAt(row - 1, col);
        }
      }
    }
    myUpAction = new MoveRunnable(-1);
    myDownAction = new MoveRunnable(1);
  }

  @Override
  protected void installDnDSupport() {
    RowsDnDSupport.install(myTable, (EditableModel)myTable.getModel());
  }

  @Override
  protected boolean isModelEditable() {
    return myTable.getModel() instanceof EditableModel;
  }
}
