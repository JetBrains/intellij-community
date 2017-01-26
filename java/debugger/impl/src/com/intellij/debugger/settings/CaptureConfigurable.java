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
package com.intellij.debugger.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ItemRemovable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.util.List;

/**
 * @author egor
 */
public class CaptureConfigurable implements SearchableConfigurable {
  @NotNull
  @Override
  public String getId() {
    return "reference.idesettings.debugger.capture";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    MyTableModel tableModel = new MyTableModel();

    JBTable table = new JBTable(tableModel);
    table.setColumnSelectionAllowed(false);

    TableColumnModel columnModel = table.getColumnModel();
    TableUtil.setupCheckboxColumn(columnModel.getColumn(MyTableModel.ENABLED_COLUMN));

    return ToolbarDecorator.createDecorator(table)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          tableModel.addRow();
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          TableUtil.removeSelectedItems(table);
        }
      }).setMoveUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          TableUtil.moveSelectedItemsUp(table);
        }
      }).setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          TableUtil.moveSelectedItemsDown(table);
        }
      }).createPanel();
  }

  private static class MyTableModel extends AbstractTableModel implements ItemRemovable {
    public static final int ENABLED_COLUMN = 0;
    public static final int CLASS_COLUMN = 1;
    public static final int METHOD_COLUMN = 2;
    public static final int PARAM_COLUMN = 3;
    public static final int INSERT_CLASS_COLUMN = 4;
    public static final int INSERT_METHOD_COLUMN = 5;
    public static final int INSERT_KEY_EXPR = 6;

    String[] columnNames = new String[]{"", "Class name", "Method name", "Param index", "Insert class name", "Insert method name", "Insert key expression"};
    List<CapturePoint> capturePoints = DebuggerSettings.getInstance().getCapturePoints();

    public String getColumnName(int column) {
      return columnNames[column];
    }

    public int getRowCount() {
      return capturePoints.size();
    }

    public int getColumnCount() {
      return columnNames.length;
    }

    public Object getValueAt(int row, int col) {
      CapturePoint point = capturePoints.get(row);
      switch (col) {
        case ENABLED_COLUMN:
          return point.myEnabled;
        case CLASS_COLUMN:
          return point.myClassName;
        case METHOD_COLUMN:
          return point.myMethodName;
        case PARAM_COLUMN:
          return point.myParamNo;
        case INSERT_CLASS_COLUMN:
          return point.myInsertClassName;
        case INSERT_METHOD_COLUMN:
          return point.myInsertMethodName;
        case INSERT_KEY_EXPR:
          return point.myInsertKeyExpression;
      }
      return null;
    }

    public boolean isCellEditable(int row, int column) {
      return true;
    }

    public void setValueAt(Object value, int row, int col) {
      CapturePoint point = capturePoints.get(row);
      switch (col) {
        case ENABLED_COLUMN:
          point.myEnabled = (boolean)value;
          break;
        case CLASS_COLUMN:
          point.myClassName = (String)value;
          break;
        case METHOD_COLUMN:
          point.myMethodName = (String)value;
          break;
        case PARAM_COLUMN:
          point.myParamNo = (int)value;
          break;
        case INSERT_CLASS_COLUMN:
          point.myInsertClassName = (String)value;
          break;
        case INSERT_METHOD_COLUMN:
          point.myInsertMethodName = (String)value;
          break;
        case INSERT_KEY_EXPR:
          point.myInsertKeyExpression = (String)value;
          break;
      }
      fireTableCellUpdated(row, col);
    }

    public Class getColumnClass(int columnIndex) {
      switch (columnIndex) {
        case ENABLED_COLUMN:
          return Boolean.class;
        case PARAM_COLUMN:
          return Integer.class;
      }
      return String.class;
    }

    public void addRow() {
      capturePoints.add(new CapturePoint());
      int lastRow = getRowCount() - 1;
      fireTableRowsInserted(lastRow, lastRow);
    }

    public void removeRow(final int row) {
      if (row >= 0 && row < getRowCount()) {
        capturePoints.remove(row);
        fireTableRowsDeleted(row, row);
      }
    }
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {

  }

  @Override
  public void reset() {

  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Capture";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }
}
