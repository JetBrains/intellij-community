/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.ui.TypeSelector;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.AbstractTableCellEditor;
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
import java.util.ArrayList;

public abstract class ParameterTablePanel extends JPanel {
  private final Project myProject;
  private final VariableData[] myVariableData;
  private final TypeSelector[] myParameterTypeSelectors;

  private final Table myTable;
  private final MyTableModel myTableModel;
  private final JButton myUpButton;
  private final JButton myDownButton;
  private final JComboBox myTypeRendererCombo;

  public VariableData[] getVariableData() {
    return myVariableData;
  }

  public static class VariableData {
    public final PsiVariable variable;
    public PsiType type;
    public String name;
    public boolean passAsParameter;

    public VariableData(PsiVariable var) {
      variable = var;
      type = var.getType();
    }

    public VariableData(PsiVariable var, PsiType type) {
      variable = var;
      this.type = type;
    }
  }

  protected abstract void updateSignature();

  protected abstract void doEnterAction();

  protected abstract void doCancelAction();

  protected boolean areTypesDirected() {
    return true;
  }

  public ParameterTablePanel(Project project, VariableData[] variableData, final PsiElement... scopeElements) {
    super(new BorderLayout());
    myProject = project;
    myVariableData = variableData;

    myTableModel = new MyTableModel();
    myTable = new Table(myTableModel);
    DefaultCellEditor defaultEditor = (DefaultCellEditor)myTable.getDefaultEditor(Object.class);
    defaultEditor.setClickCountToStart(1);

    myTable.setTableHeader(null);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getColumnModel().getColumn(MyTableModel.CHECKMARK_COLUMN).setCellRenderer(new CheckBoxTableCellRenderer());
    myTable.getColumnModel().getColumn(MyTableModel.CHECKMARK_COLUMN).setMaxWidth(new JCheckBox().getPreferredSize().width);
    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_NAME_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        VariableData data = getVariableData()[row];
        setText(data.name);
        return this;
      }
    });

    myParameterTypeSelectors = new TypeSelector[getVariableData().length];
    for (int i = 0; i < myParameterTypeSelectors.length; i++) {
      final PsiVariable variable = getVariableData()[i].variable;
      final PsiExpression[] occurrences = findVariableOccurrences(scopeElements, variable);
      final TypeSelectorManager manager = new TypeSelectorManagerImpl(myProject, getVariableData()[i].type, occurrences, areTypesDirected()) {
        @Override
        protected boolean isUsedAfter() {
          return ParameterTablePanel.this.isUsedAfter(variable);
        }
      };
      myParameterTypeSelectors[i] = manager.getTypeSelector();
      getVariableData()[i].type = myParameterTypeSelectors[i].getSelectedType(); //reverse order
    }

    myTypeRendererCombo = new JComboBox(getVariableData());
    myTypeRendererCombo.setOpaque(true);
    myTypeRendererCombo.setBorder(null);

    myTypeRendererCombo.setRenderer(new DefaultListCellRenderer() {

      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        setText(((VariableData)value).type.getPresentableText());
        return this;
      }
    });
    
    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_TYPE_COLUMN).setCellEditor(new AbstractTableCellEditor() {
      TypeSelector myCurrentSelector;
      public Object getCellEditorValue() {
        return myCurrentSelector.getSelectedType();
      }

      public Component getTableCellEditorComponent(final JTable table,
                                                   final Object value,
                                                   final boolean isSelected,
                                                   final int row,
                                                   final int column) {
        myCurrentSelector = myParameterTypeSelectors[row];
        return myCurrentSelector.getComponent();
      }
    });

    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_TYPE_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (myParameterTypeSelectors[row].getComponent() instanceof JComboBox) {
          myTypeRendererCombo.setSelectedIndex(row);
          return myTypeRendererCombo;
        }

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        VariableData data = getVariableData()[row];
        setText(data.type.getPresentableText());
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
            if (!getVariableData()[row].passAsParameter) {
              valueToBeSet = true;
              break;
            }
          }
          for (int row : rows) {
            getVariableData()[row].passAsParameter = valueToBeSet;
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
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
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
    if (getVariableData().length <= 1) {
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
    else {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }
    updateMoveButtons();
  }

  protected boolean isUsedAfter(PsiVariable variable) {
    return false;
  }

  public static PsiExpression[] findVariableOccurrences(final PsiElement[] scopeElements, final PsiVariable variable) {
    final ArrayList<PsiExpression> result = new ArrayList<PsiExpression>();
    for (final PsiElement element : scopeElements) {
      element.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitReferenceExpression(final PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          if (!expression.isQualified() && expression.isReferenceTo(variable)) {
            result.add(expression);
          }
        }
      });
    }
    return result.toArray(new PsiExpression[result.size()]);
  }

  private void updateMoveButtons() {
    int row = myTable.getSelectedRow();
    if (0 <= row && row < getVariableData().length) {
      myUpButton.setEnabled(row > 0);
      myDownButton.setEnabled(row < getVariableData().length - 1);
    }
    else {
      myUpButton.setEnabled(false);
      myDownButton.setEnabled(false);
    }
  }

  private void moveSelectedItem(int moveIncrement) {
    int row = myTable.getSelectedRow();
    if (row < 0 || row >= getVariableData().length) return;
    int targetRow = row + moveIncrement;
    if (targetRow < 0 || targetRow >= getVariableData().length) return;

    VariableData currentItem = getVariableData()[row];
    getVariableData()[row] = getVariableData()[targetRow];
    getVariableData()[targetRow] = currentItem;

    TypeSelector currentSelector = myParameterTypeSelectors[row];
    myParameterTypeSelectors[row] = myParameterTypeSelectors[targetRow];
    myParameterTypeSelectors[targetRow] = currentSelector;
    
    myTypeRendererCombo.setModel(new DefaultComboBoxModel(getVariableData()));

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
    public static final int PARAMETER_TYPE_COLUMN = 1;
    public static final int PARAMETER_NAME_COLUMN = 2;

    public int getRowCount() {
      return getVariableData().length;
    }

    public int getColumnCount() {
      return 3;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN: {
          return getVariableData()[rowIndex].passAsParameter ? Boolean.TRUE : Boolean.FALSE;
        }
        case PARAMETER_NAME_COLUMN: {
          return getVariableData()[rowIndex].name;
        }
        case PARAMETER_TYPE_COLUMN: {
          return getVariableData()[rowIndex].type.getPresentableText();
        }
      }
      assert false;
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKMARK_COLUMN: {
          getVariableData()[rowIndex].passAsParameter = ((Boolean)aValue).booleanValue();
          fireTableRowsUpdated(rowIndex, rowIndex);
          myTable.getSelectionModel().setSelectionInterval(rowIndex, rowIndex);
          updateSignature();
          break;
        }
        case PARAMETER_NAME_COLUMN: {
          VariableData data = getVariableData()[rowIndex];
          String name = (String)aValue;
          if (JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(name)) {
            data.name = name;
          }
          updateSignature();
          break;
        }
        case PARAMETER_TYPE_COLUMN: {
          VariableData data = getVariableData()[rowIndex];
          data.type = (PsiType)aValue;
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
          return isEnabled() && getVariableData()[rowIndex].passAsParameter;
        case PARAMETER_TYPE_COLUMN:
          return isEnabled() && getVariableData()[rowIndex].passAsParameter && !(myParameterTypeSelectors[rowIndex].getComponent() instanceof JLabel);
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

  private class CheckBoxTableCellRenderer extends BooleanTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      rendererComponent.setEnabled(ParameterTablePanel.this.isEnabled());
      return rendererComponent;
    }
  }
}
