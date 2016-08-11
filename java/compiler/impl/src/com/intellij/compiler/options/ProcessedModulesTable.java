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
package com.intellij.compiler.options;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TableUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ProcessedModulesTable extends JPanel {
  private JBTable myTable = null;
  private MyTableModel myTableModel = null;

  public ProcessedModulesTable(final Project project) {
    super(new BorderLayout());

    myTableModel = new MyTableModel(project);
    myTable = new JBTable(myTableModel);
    myTable.getEmptyText().setText("No modules configured");

    //myTable.setShowGrid(false);
    myTable.setIntercellSpacing(JBUI.emptySize());
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);

    final TableColumnModel columnModel = myTable.getColumnModel();

    final TableColumn dirNameColumn = columnModel.getColumn(myTableModel.DIRNAME_COLUMN_INDEX);
    final String title = "Generated Sources Directory Name";
    dirNameColumn.setHeaderValue(title);
    final JTableHeader tableHeader = myTable.getTableHeader();
    final FontMetrics metrics = tableHeader.getFontMetrics(tableHeader.getFont());
    final int preferredWidth = metrics.stringWidth(title) + 12;
    dirNameColumn.setPreferredWidth(preferredWidth);
    dirNameColumn.setMaxWidth(preferredWidth + 20);
    dirNameColumn.setCellRenderer(new MyElementColumnCellRenderer());

    final TableColumn moduleColumn = columnModel.getColumn(myTableModel.ELEMENT_COLUMN_INDEX);
    moduleColumn.setHeaderValue("Module");
    moduleColumn.setCellRenderer(new MyElementColumnCellRenderer());

    final JPanel panel = ToolbarDecorator.createDecorator(myTable)
      .disableUpDownActions()
      .setPreferredSize(JBUI.size(100, 155))
      .createPanel();
    add(panel, BorderLayout.CENTER);

    final SpeedSearchBase<JBTable> speedSearch = new SpeedSearchBase<JBTable>(myTable) {
      public int getSelectedIndex() {
        return myTable.getSelectedRow();
      }

      @Override
      protected int convertIndexToModel(int viewIndex) {
        return myTable.convertRowIndexToModel(viewIndex);
      }

      public Object[] getAllElements() {
        final int count = myTableModel.getRowCount();
        Object[] elements = new Object[count];
        for (int idx = 0; idx < count; idx++) {
          elements[idx] = myTableModel.getModuleAt(idx);
        }
        return elements;
      }

      public String getElementText(Object element) {
        return ((Module)element).getName() + " (" + FileUtil.toSystemDependentName(((Module)element).getModuleFilePath()) + ")";
      }

      public void selectElement(Object element, String selectedText) {
        final int count = myTableModel.getRowCount();
        for (int row = 0; row < count; row++) {
          if (element.equals(myTableModel.getModuleAt(row))) {
            final int viewRow = myTable.convertRowIndexToView(row);
            myTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            TableUtil.scrollSelectionToVisible(myTable);
            break;
          }
        }
      }
    };
    speedSearch.setComparator(new SpeedSearchComparator(false));
  }

  public void refresh() {
    myTableModel.fireTableDataChanged();
  }

  public void refresh(Module element) {
    final int row = myTableModel.getElementRow(element);
    if (row >= 0) {
      myTableModel.fireTableRowsUpdated(row, row);
    }
  }

  private int[] mySavedSelection = null;
  public void saveSelection() {
    mySavedSelection = myTable.getSelectedRows();
  }

  public void restoreSelection() {
    if (mySavedSelection != null) {
      TableUtil.selectRows(myTable, mySavedSelection);
      mySavedSelection = null;
    }
  }

  public void addModule(Module element, String dirName) {
    myTableModel.addElement(element, dirName);
    selectRow(myTableModel.getRowCount() - 1);
    myTable.requestFocus();
  }

  public void removeModule(Module element) {
    final int elementRow = myTableModel.getElementRow(element);
    if (elementRow < 0) {
      return; // no such element
    }
    final boolean wasSelected = myTable.getSelectionModel().isSelectedIndex(elementRow);

    myTableModel.removeElement(element);

    if (wasSelected) {
      final int rowCount = myTableModel.getRowCount();
      if (rowCount > 0) {
        selectRow(elementRow % rowCount);
      }
      else {
        myTable.getSelectionModel().clearSelection();
      }
    }
    myTable.requestFocus();
  }

  public void removeAllElements() {
    myTableModel.removeAllElements();
    myTable.getSelectionModel().clearSelection();
  }

  private void selectRow(final int row) {
    myTable.getSelectionModel().setSelectionInterval(row, row);
    myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));
  }

  @Nullable
  public Module getSelectedElement() {
    final int selectedRow = getSelectedElementRow();
    return selectedRow < 0? null : myTableModel.getModuleAt(selectedRow);
  }

  public int getSelectedElementRow() {
    return myTable.getSelectedRow();
  }

  public List<Module> getSelectedElements() {
    final List<Module> elements = new ArrayList<>();
    final int[] selectedRows = myTable.getSelectedRows();
    for (int selectedRow : selectedRows) {
      if (selectedRow < 0) {
        continue;
      }
      elements.add(myTableModel.getModuleAt(selectedRow));
    }
    return elements;
  }

  public void selectElements(Collection<? extends Module> elements) {
    if (elements.size() == 0) {
      myTable.clearSelection();
      return;
    }
    final int[] rows = getElementsRows(elements);
    TableUtil.selectRows(myTable, rows);
    TableUtil.scrollSelectionToVisible(myTable);
    myTable.requestFocus();
  }

  private int[] getElementsRows(final Collection<? extends Module> elements) {
    final int[] rows = new int[elements.size()];
    int index = 0;
    for (final Module element : elements) {
      rows[index++] = myTableModel.getElementRow(element);
    }
    return rows;
  }

  public List<Pair<Module, String>> getAllModules() {
    final int count = myTableModel.getRowCount();
    List<Pair<Module, String>> elements = new ArrayList<>();
    for (int idx = 0; idx < count; idx++) {
      final Module module = myTableModel.getModuleAt(idx);
      elements.add(Pair.create(module, myTableModel.getGenDirName(module)));
    }
    return elements;
  }

  public void sort(Comparator<Module> comparator) {
    myTableModel.sort(comparator);
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myTable.setRowSelectionAllowed(enabled);
    myTableModel.fireTableDataChanged();
  }

  public void stopEditing() {
    TableCellEditor editor = myTable.getCellEditor();
    if (editor != null) {
      editor.stopCellEditing();
    }
  }

  public JComponent getComponent() {
    return myTable;
  }

  public void clear() {
    myTableModel.clear();
  }

  public int getElementCount() {
    return myTableModel.getRowCount();
  }

  public Module getElementAt(int row) {
    return myTableModel.getModuleAt(row);
  }

  private final class MyTableModel extends AbstractTableModel implements EditableModel {
    private final List<Module> myElements = new ArrayList<>();
    private final Map<Module, String> myDirNameMap = new HashMap<>();
    public final int ELEMENT_COLUMN_INDEX = 0;
    public final int DIRNAME_COLUMN_INDEX = 1;
    private final Project myProject;

    private MyTableModel(Project project) {
      myProject = project;
    }

    public void sort(Comparator<Module> comparator) {
      Collections.sort(myElements, comparator);
      fireTableDataChanged();
    }

    public List<Module> getAllModules() {
      return Collections.unmodifiableList(myElements);
    }

    public Module getModuleAt(int index) {
      return myElements.get(index);
    }

    public String getGenDirName(Module module) {
      return myDirNameMap.get(module);
    }

    void addElement(Module module, final String dirName) {
      myElements.add(module);
      if (dirName != null && dirName.length() > 0) {
        myDirNameMap.put(module, dirName);
      }
      int row = myElements.size() - 1;
      fireTableRowsInserted(row, row);
    }

    @Override
    public void addRow() {
      final Set<Module> projectModules = new HashSet<>(Arrays.asList(ModuleManager.getInstance(myProject).getModules()));
      projectModules.removeAll(getAllModules());
      final ChooseModulesDialog chooser =
        new ChooseModulesDialog(ProcessedModulesTable.this, new ArrayList<>(projectModules), "ChooseModule");
      if (chooser.showAndGet()) {
        final List<Module> chosen = chooser.getChosenElements();
        for (Module module : chosen) {
          addElement(module, null);
        }
      }
    }

    public void removeRow(int idx) {
      final Module element = myElements.remove(idx);
      myDirNameMap.remove(element);
      fireTableRowsDeleted(idx, idx);
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return false;
    }

    public void removeElement(Module element) {
      final boolean reallyRemoved = myElements.remove(element);
      if (reallyRemoved) {
        myDirNameMap.remove(element);
        fireTableDataChanged();
      }
    }

    public int getElementRow(Module element) {
      return myElements.indexOf(element);
    }

    public void removeAllElements() {
      myElements.clear();
      fireTableDataChanged();
    }

    public int getRowCount() {
      return myElements.size();
    }

    public int getColumnCount() {
      return 2;
    }

    @Nullable
    public Object getValueAt(int rowIndex, int columnIndex) {
      Module element = myElements.get(rowIndex);
      if (columnIndex == ELEMENT_COLUMN_INDEX) {
        return element;
      }
      if (columnIndex == DIRNAME_COLUMN_INDEX) {
        return myDirNameMap.get(element);
      }
      return null;
    }

    public void setValueAt(Object value, int rowIndex, int columnIndex) {
      if (columnIndex == DIRNAME_COLUMN_INDEX) {
        final Module module = myElements.get(rowIndex);
        if (value != null) {
          String dir = FileUtil.toSystemIndependentName((String)value);
          while (dir.startsWith("/")) {
            dir = dir.substring(1);
          }
          if (dir.length() > 0) {
            myDirNameMap.put(module, dir);
          }
          else {
            myDirNameMap.remove(module);
          }
        }
        else {
          myDirNameMap.remove(module);
        }
        fireTableRowsUpdated(rowIndex, rowIndex);
      }
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == DIRNAME_COLUMN_INDEX) {
        return String.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (!ProcessedModulesTable.this.isEnabled()) {
        return false;
      }
      if (columnIndex == DIRNAME_COLUMN_INDEX) {
        return true;
      }
      return false;
    }

    public void clear() {
      myElements.clear();
      myDirNameMap.clear();
      fireTableDataChanged();
    }
  }

  private class MyElementColumnCellRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final Color color = UIUtil.getTableFocusCellBackground();
      Component component;
      final Module module = value instanceof Module? (Module)value : null;
      try {
        UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, table.getSelectionBackground());
        component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        if (module != null) {
          setText(module.getName() + " (" + FileUtil.toSystemDependentName(module.getModuleFilePath()) + ")");
        }
        if (component instanceof JLabel) {
          ((JLabel)component).setBorder(noFocusBorder);
        }
      }
      finally {
        UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, color);
      }
      component.setEnabled(ProcessedModulesTable.this.isEnabled());
      if (component instanceof JLabel) {
        final Icon icon = module != null ? ModuleType.get(module).getIcon() : null;
        JLabel label = (JLabel)component;
        label.setIcon(icon);
        label.setDisabledIcon(icon);
      }
      component.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
      return component;
    }
  }
}
