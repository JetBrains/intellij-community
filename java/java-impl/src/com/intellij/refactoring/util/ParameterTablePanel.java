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
package com.intellij.refactoring.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.ui.TypeSelector;
import com.intellij.refactoring.ui.TypeSelectorManager;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBComboBoxLabel;
import com.intellij.ui.components.editors.JBComboBoxTableCellEditorComponent;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Function;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ParameterTablePanel extends JPanel {
  private final Project myProject;
  private final VariableData[] myVariableData;
  private final TypeSelector[] myParameterTypeSelectors;

  private final JBTable myTable;
  private final MyTableModel myTableModel;
  private final JComboBox myTypeRendererCombo;

  public VariableData[] getVariableData() {
    return myVariableData;
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
    myTable = new JBTable(myTableModel);
    DefaultCellEditor defaultEditor = (DefaultCellEditor)myTable.getDefaultEditor(Object.class);
    defaultEditor.setClickCountToStart(1);


    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.setCellSelectionEnabled(true);
    TableColumn checkboxColumn = myTable.getColumnModel().getColumn(MyTableModel.CHECKMARK_COLUMN);
    TableUtil.setupCheckboxColumn(checkboxColumn);
    checkboxColumn.setCellRenderer(new CheckBoxTableCellRenderer());
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
    myTypeRendererCombo.setRenderer(new ListCellRendererWrapper<VariableData>() {
      @Override
      public void customize(JList list, VariableData value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(value.type.getPresentableText());
        }
      }
    });


    final TableColumn typeColumn = myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_TYPE_COLUMN);
    typeColumn.setCellEditor(new AbstractTableCellEditor() {
      TypeSelector myCurrentSelector;
      final JBComboBoxTableCellEditorComponent myEditorComponent = new JBComboBoxTableCellEditorComponent();

      @Nullable
      public Object getCellEditorValue() {
        return myEditorComponent.getEditorValue();
      }

      public Component getTableCellEditorComponent(final JTable table,
                                                   final Object value,
                                                   final boolean isSelected,
                                                   final int row,
                                                   final int column) {
        myEditorComponent.setCell(table, row, column);
        myEditorComponent.setOptions(myParameterTypeSelectors[row].getTypes());
        myEditorComponent.setDefaultValue(getVariableData()[row].type);
        myEditorComponent.setToString(new Function<Object, String>() {
          @Override
          public String fun(Object o) {
            return ((PsiType)o).getPresentableText();
          }
        });

        myCurrentSelector = myParameterTypeSelectors[row];
        return myEditorComponent;
      }
    });



    myTable.getColumnModel().getColumn(MyTableModel.PARAMETER_TYPE_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
      private final JBComboBoxLabel myLabel = new JBComboBoxLabel();

      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        myLabel.setText(String.valueOf(value));
        myLabel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        myLabel.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        if (isSelected) {
          myLabel.setSelectionIcon();
        } else {
          myLabel.setRegularIcon();
        }
        return myLabel;
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
    //// F2 should edit the name
    //inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "edit_parameter_name");
    //actionMap.put("edit_parameter_name", new AbstractAction() {
    //  public void actionPerformed(ActionEvent e) {
    //    if (!myTable.isEditing()) {
    //      int row = myTable.getSelectedRow();
    //      if (row >= 0 && row < myTableModel.getRowCount()) {
    //        TableUtil.editCellAt(myTable, row, MyTableModel.PARAMETER_NAME_COLUMN);
    //      }
    //    }
    //  }
    //});

    //// make ENTER work when the table has focus
    //inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "invokeImpl");
    //actionMap.put("invokeImpl", new AbstractAction() {
    //  public void actionPerformed(ActionEvent e) {
    //    TableCellEditor editor = myTable.getCellEditor();
    //    if (editor != null) {
    //      editor.stopCellEditing();
    //    }
    //    else {
    //      doEnterAction();
    //    }
    //  }
    //});

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


    JPanel listPanel = ToolbarDecorator.createDecorator(myTable).disableAddAction().disableRemoveAction().createPanel();
    add(listPanel, BorderLayout.CENTER);

    if (getVariableData().length > 1) {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }
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


  public void setEnabled(boolean enabled) {
    myTable.setEnabled(enabled);
    super.setEnabled(enabled);
  }

  private class MyTableModel extends AbstractTableModel implements EditableModel {
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
          return getVariableData()[rowIndex].passAsParameter;
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
          if (PsiNameHelper.getInstance(myProject).isIdentifier(name)) {
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

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case CHECKMARK_COLUMN: return "";
        case PARAMETER_TYPE_COLUMN: return "Type";
        case PARAMETER_NAME_COLUMN: return "Name";
      }
      return "";
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

      final VariableData currentItem = getVariableData()[row];
      getVariableData()[row] = getVariableData()[targetRow];
      getVariableData()[targetRow] = currentItem;

      TypeSelector currentSelector = myParameterTypeSelectors[row];
      myParameterTypeSelectors[row] = myParameterTypeSelectors[targetRow];
      myParameterTypeSelectors[targetRow] = currentSelector;

      myTypeRendererCombo.setModel(new DefaultComboBoxModel(getVariableData()));

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

  private class CheckBoxTableCellRenderer extends BooleanTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      rendererComponent.setEnabled(ParameterTablePanel.this.isEnabled());
      return rendererComponent;
    }
  }
}
