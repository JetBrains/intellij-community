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

import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

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

  protected void updateButtons() {
    final CommonActionsPanel p = getActionsPanel();
    if (p != null) {
      if (myTable.isEnabled()) {
        final int index = myTable.getSelectedRow();
        final int size = myTable.getModel().getRowCount();
        if (0 <= index && index < size) {
          final boolean downEnable = myTable.getSelectionModel().getMaxSelectionIndex() < size - 1;
          final boolean upEnable = myTable.getSelectionModel().getMinSelectionIndex() > 0;
          final boolean editEnabled = myTable.getSelectedRowCount() == 1;
          p.setEnabled(CommonActionsPanel.Buttons.EDIT, editEnabled);
          p.setEnabled(CommonActionsPanel.Buttons.REMOVE, true);
          p.setEnabled(CommonActionsPanel.Buttons.UP, upEnable);
          p.setEnabled(CommonActionsPanel.Buttons.DOWN, downEnable);
        }
        else {
          p.setEnabled(CommonActionsPanel.Buttons.EDIT, false);
          p.setEnabled(CommonActionsPanel.Buttons.REMOVE, false);
          p.setEnabled(CommonActionsPanel.Buttons.UP, false);
          p.setEnabled(CommonActionsPanel.Buttons.DOWN, false);
        }
        p.setEnabled(CommonActionsPanel.Buttons.ADD, myProducer == null || myProducer.canCreateElement());
      }
      else {
        p.setEnabled(CommonActionsPanel.Buttons.ADD, false);
        p.setEnabled(CommonActionsPanel.Buttons.EDIT, false);
        p.setEnabled(CommonActionsPanel.Buttons.REMOVE, false);
        p.setEnabled(CommonActionsPanel.Buttons.UP, false);
        p.setEnabled(CommonActionsPanel.Buttons.DOWN, false);
      }
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

        updateScroller(table, table.getCellEditor() instanceof Animated);
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
        TableUtil.stopEditing(table);
        int index = table.getSelectedRow();
        if (0 <= index && index < table.getModel().getRowCount()) {
          tableModel.removeRow(index);
          if (index < table.getModel().getRowCount()) {
            table.setRowSelectionInterval(index, index);
          }
          else {
            if (index > 0) {
              table.setRowSelectionInterval(index - 1, index - 1);
            }
          }
          updateButtons();
        }

        table.requestFocus();

        updateScroller(table, false);
      }
    };

    myUpAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {        
        final int row = table.getEditingRow();
        final int col = table.getEditingColumn();
        TableUtil.stopEditing(table);
        final int[] indexes = table.getSelectedRows();
        for (int index : indexes) {
          if (0 < index && index < table.getModel().getRowCount()) {
            tableModel.exchangeRows(index, index - 1);
            table.setRowSelectionInterval(index - 1, index - 1);
          }
        }        
        table.requestFocus();
        if (row > 0 && col != -1) {
          table.editCellAt(row - 1, col);
        }
      }
    };

    myDownAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final int row = table.getEditingRow();
        final int col = table.getEditingColumn();

        TableUtil.stopEditing(table);
        final int[] indexes = table.getSelectedRows();
        for (int index : indexes) {
          if (0 <= index && index < table.getModel().getRowCount() - 1) {
            tableModel.exchangeRows(index, index + 1);
            table.setRowSelectionInterval(index + 1, index + 1);
          }
        }
        table.requestFocus();
        if (row < table.getRowCount() - 1 && col != -1) {
          table.editCellAt(row + 1, col);
        }
      }
    };
  }

  private static void updateScroller(JTable table, boolean temporaryHideVerticalScrollBar) {
    JScrollPane scrollPane = UIUtil.getParentOfType(JScrollPane.class, table);
    if (scrollPane != null) {
      if (temporaryHideVerticalScrollBar) {
        final JScrollBar bar = scrollPane.getVerticalScrollBar();
        if (bar == null || !bar.isVisible()) {
          scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        }
      }
      scrollPane.revalidate();
      scrollPane.repaint();
    }
  }

  @Override
  protected void installDnDSupport() {
    RowsDnDSupport.install(myTable, (EditableModel)myTable.getModel());
  }

  protected boolean isModelEditable() {
    return myTable.getModel() instanceof EditableModel;
  }
}
