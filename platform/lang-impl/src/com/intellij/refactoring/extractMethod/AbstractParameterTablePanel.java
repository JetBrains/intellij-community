/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author oleg
 * It`s a modified copy of com.intellij.refactoring.util.AbstractParameterTablePanel
 */
public abstract class AbstractParameterTablePanel extends JPanel {
  private AbstractVariableData[] myVariableData;

  private Table myTable;
  private MyTableModel myTableModel;
  private JButton myUpButton;
  private JButton myDownButton;
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
    myTable = new Table(myTableModel);
    DefaultCellEditor defaultEditor = (DefaultCellEditor)myTable.getDefaultEditor(Object.class);
    defaultEditor.setClickCountToStart(1);

    myTable.setTableHeader(null);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getColumnModel().getColumn(MyTableModel.CHECKMARK_COLUMN).setMaxWidth(new JCheckBox().getPreferredSize().width);
    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_NAME_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        AbstractVariableData data = myVariableData[row];
        setText(data.name);
        return this;
      }
    });


    myTable.setPreferredScrollableViewportSize(new Dimension(250, myTable.getRowHeight() * 5));
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    @NonNls final InputMap inputMap = myTable.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    @NonNls final ActionMap actionMap = myTable.getActionMap();
    actionMap.put("enable_disable", new AbstractAction() {
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
      public void actionPerformed(ActionEvent e) {
        if (!myTable.isEditing()) {
          int row = myTable.getSelectedRow();
          if (row >= 0 && row < myTableModel.getRowCount()) {
            TableUtil.editCellAt(myTable, row, MyTableModel.PARAMETER_NAME_COLUMN);
          }
        }
      }
    });

    // make ENTER work when the table has focus
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "invokeImpl");
    actionMap.put("invokeImpl", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TableCellEditor editor = myTable.getCellEditor();
        if (editor != null) {
          editor.stopCellEditing();
        }
        else {
          doEnterAction();
        }
      }
    });

    // make ESCAPE work when the table has focus
    actionMap.put("doCancel", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        TableCellEditor editor = myTable.getCellEditor();
        if (editor != null) {
          editor.stopCellEditing();
        }
        else {
          doCancelAction();
        }
      }
    });

    JPanel listPanel = new JPanel(new BorderLayout());
    JBScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    listPanel.add(scrollPane, BorderLayout.CENTER);
    listPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    add(listPanel, BorderLayout.CENTER);

    JPanel buttonsPanel = new JPanel();
    buttonsPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    add(buttonsPanel, BorderLayout.EAST);

    buttonsPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets = new Insets(2, 4, 2, 4);

    myUpButton = new JButton();
    myUpButton.setText(UIBundle.message("row.move.up"));
    myUpButton.setDefaultCapable(false);
    buttonsPanel.add(myUpButton, gbConstraints);

    myDownButton = new JButton();
    myDownButton.setText(UIBundle.message("row.move.down"));
    myDownButton.setDefaultCapable(false);
    buttonsPanel.add(myDownButton, gbConstraints);

    gbConstraints.weighty = 1;
    buttonsPanel.add(new JPanel(), gbConstraints);

    myUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) {
          final boolean isStopped = myTable.getCellEditor().stopCellEditing();
          if (!isStopped) return;
        }
        moveSelectedItem(-1);
        updateSignature();
        myTable.requestFocus();
      }
    });

    myDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) {
          final boolean isStopped = myTable.getCellEditor().stopCellEditing();
          if (!isStopped) return;
        }
        moveSelectedItem(+1);
        updateSignature();
        myTable.requestFocus();
      }
    });

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateMoveButtons();
      }
    });
    if (myVariableData.length <= 1) {
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
    else {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }
    updateMoveButtons();
  }

  private void updateMoveButtons() {
    int row = myTable.getSelectedRow();
    if (0 <= row && row < myVariableData.length) {
      myUpButton.setEnabled(row > 0);
      myDownButton.setEnabled(row < myVariableData.length - 1);
    }
    else {
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
  }

  private void moveSelectedItem(int moveIncrement) {
    int row = myTable.getSelectedRow();
    if (row < 0 || row >= myVariableData.length) return;
    int targetRow = row + moveIncrement;
    if (targetRow < 0 || targetRow >= myVariableData.length) return;

    AbstractVariableData currentItem = myVariableData[row];
    myVariableData[row] = myVariableData[targetRow];
    myVariableData[targetRow] = currentItem;

    myTableModel.fireTableRowsUpdated(Math.min(targetRow, row), Math.max(targetRow, row));
    myTable.getSelectionModel().setSelectionInterval(targetRow, targetRow);
  }

  public void setEnabled(boolean enabled) {
    myTable.setEnabled(enabled);
    if (!enabled) {
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
    else {
      updateMoveButtons();
    }
    super.setEnabled(enabled);
  }

  private class MyTableModel extends AbstractTableModel {
    public static final int CHECKMARK_COLUMN = 0;
    public static final int PARAMETER_NAME_COLUMN = 1;

    public int getRowCount() {
      return myVariableData.length;
    }

    public int getColumnCount() {
      return 2;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN: {
          return myVariableData[rowIndex].passAsParameter ? Boolean.TRUE : Boolean.FALSE;
        }
        case PARAMETER_NAME_COLUMN: {
          return myVariableData[rowIndex].name;
        }
      }
      assert false;
      return null;
    }

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

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECKMARK_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }
  }
}
