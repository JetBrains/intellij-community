/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.extractMethod;

import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * @author oleg
 * It`s a modified copy of com.intellij.refactoring.util.ParameterTablePanel
 */
public abstract class AbstractParameterTablePanel extends JPanel {
  private AbstractVariableData[] myVariableData;

  private JBTable myTable;
  private MyTableModel myTableModel;
  private final ExtractMethodValidator myValidator;

  protected abstract void updateSignature();

  protected abstract void doEnterAction();

  protected abstract void doCancelAction();

  public void setVariableData(AbstractVariableData[] variableData) {
    myVariableData = variableData;
  }

  public AbstractParameterTablePanel(final ExtractMethodValidator validator) {
    super(new BorderLayout());
    myValidator = validator;
  }

  public void init() {
    myTableModel = new MyTableModel();
    myTable = new JBTable(myTableModel);
    DefaultCellEditor defaultEditor = (DefaultCellEditor)myTable.getDefaultEditor(Object.class);
    defaultEditor.setClickCountToStart(1);

    myTable.setTableHeader(null);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    TableUtil.setupCheckboxColumn(myTable, MyTableModel.CHECKMARK_COLUMN);
    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_NAME_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        AbstractVariableData data = myVariableData[row];
        setText(data.name);
        return this;
      }
    });


    myTable.setPreferredScrollableViewportSize(new Dimension(250, myTable.getRowHeight() * 5));
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(JBUI.emptySize());
    @NonNls final InputMap inputMap = myTable.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    @NonNls final ActionMap actionMap = myTable.getActionMap();
    actionMap.put("enable_disable", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;
        int[] rows = myTable.getSelectedRows();
        if (rows.length > 0) {
          boolean valueToBeSet = false;
          for (int row : rows) {
            if (!myVariableData[row].passAsParameter) {
              valueToBeSet = true;
              break;
            }
          }
          for (int row : rows) {
            myVariableData[row].passAsParameter = valueToBeSet;
          }
          myTableModel.fireTableRowsUpdated(rows[0], rows[rows.length - 1]);
          TableUtil.selectRows(myTable, rows);
        }
      }
    });
    // F2 should edit the name
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "edit_parameter_name");
    actionMap.put("edit_parameter_name", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!myTable.isEditing()) {
          int row = myTable.getSelectedRow();
          if (row >= 0 && row < myTableModel.getRowCount()) {
            TableUtil.editCellAt(myTable, row, MyTableModel.PARAMETER_NAME_COLUMN);
          }
        }
      }
    });

    JPanel listPanel = ToolbarDecorator.createDecorator(myTable).disableAddAction().disableRemoveAction().createPanel();
    add(listPanel, BorderLayout.CENTER);

    if (myVariableData.length > 1) {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    myTable.setEnabled(enabled);
    super.setEnabled(enabled);
  }

  public AbstractVariableData[] getVariableData() {
    return myVariableData;
  }

  private class MyTableModel extends AbstractTableModel implements EditableModel {
    public static final int CHECKMARK_COLUMN = 0;
    public static final int PARAMETER_NAME_COLUMN = 1;

    @Override
    public int getRowCount() {
      return myVariableData.length;
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN: {
          return myVariableData[rowIndex].passAsParameter;
        }
        case PARAMETER_NAME_COLUMN: {
          return myVariableData[rowIndex].name;
        }
      }
      assert false;
      return null;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN: {
          myVariableData[rowIndex].passAsParameter = ((Boolean)aValue).booleanValue();
          fireTableRowsUpdated(rowIndex, rowIndex);
          myTable.getSelectionModel().setSelectionInterval(rowIndex, rowIndex);
          updateSignature();
          break;
        }
        case PARAMETER_NAME_COLUMN: {
          AbstractVariableData data = myVariableData[rowIndex];
          String name = (String)aValue;
          if (myValidator.isValidName(name)) {
            data.name = name;
          }
          updateSignature();
          break;
        }
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN:
          return isEnabled();
        case PARAMETER_NAME_COLUMN:
          return isEnabled() && myVariableData[rowIndex].passAsParameter;
        default:
          return false;
      }
    }

    @Override
    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECKMARK_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    @Override
    public void addRow() {
      throw new IllegalAccessError("Not implemented");
    }

    @Override
    public void removeRow(int index) {
      throw new IllegalAccessError("Not implemented");
    }

    @Override
    public void exchangeRows(int row, int targetRow) {
      if (row < 0 || row >= getVariableData().length) return;
      if (targetRow < 0 || targetRow >= getVariableData().length) return;

      final AbstractVariableData currentItem = getVariableData()[row];
      getVariableData()[row] = getVariableData()[targetRow];
      getVariableData()[targetRow] = currentItem;

      myTableModel.fireTableRowsUpdated(Math.min(targetRow, row), Math.max(targetRow, row));
      myTable.getSelectionModel().setSelectionInterval(targetRow, targetRow);
      updateSignature();
    }

    @Override
    public boolean canExchangeRows(int row, int targetRow) {
      if (row < 0 || row >= getVariableData().length) return false;
      if (targetRow < 0 || targetRow >= getVariableData().length) return false;
      return true;
    }
  }
}
