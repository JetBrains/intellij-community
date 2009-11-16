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
package com.intellij.compiler.options;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

public class ProcessedModulesChooser extends JPanel {
  private Table myTable = null;
  private MyTableModel myTableModel = null;
  private boolean myColorUnmarkedElements = true;

  public ProcessedModulesChooser() {
    super(new BorderLayout());

    myTableModel = new MyTableModel();
    myTable = new Table(myTableModel);
    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTable);
    pane.setPreferredSize(new Dimension(100, 155));

    final TableColumnModel columnModel = myTable.getColumnModel();

    final int checkmarkWidth = new JCheckBox().getPreferredSize().width;
    final CheckMarkColumnCellRenderer checkmarkRenderer = new CheckMarkColumnCellRenderer(myTable.getDefaultRenderer(Boolean.class));

    final TableColumn checkMarkColumn = columnModel.getColumn(myTableModel.CHECK_MARK_COLUM_INDEX);
    checkMarkColumn.setHeaderValue("");
    checkMarkColumn.setPreferredWidth(checkmarkWidth);
    checkMarkColumn.setMaxWidth(checkmarkWidth);
    checkMarkColumn.setCellRenderer(checkmarkRenderer);

    TableColumn storeUnderContent = columnModel.getColumn(myTableModel.STORE_UNDER_CONTENT_COLUM_INDEX);
    final String title = "Generate Sources Under Content";
    storeUnderContent.setHeaderValue(title);
    final JTableHeader tableHeader = myTable.getTableHeader();
    final FontMetrics metrics = tableHeader.getFontMetrics(tableHeader.getFont());
    final int preferredWidth = metrics.stringWidth(title) + 12;
    storeUnderContent.setPreferredWidth(preferredWidth);
    storeUnderContent.setMaxWidth(preferredWidth);
    storeUnderContent.setCellRenderer(checkmarkRenderer);

    final TableColumn moduleColumn = columnModel.getColumn(myTableModel.ELEMENT_COLUMN_INDEX);
    moduleColumn.setHeaderValue("Module");
    moduleColumn.setCellRenderer(new MyElementColumnCellRenderer());

    add(pane, BorderLayout.CENTER);
    myTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final int[] selectedRows = myTable.getSelectedRows();
          boolean currentlyMarked = true;
          for (int selectedRow : selectedRows) {
            currentlyMarked = myTableModel.isMarked(selectedRow);
            if (!currentlyMarked) {
              break;
            }
          }
          myTableModel.setMarked(selectedRows, !currentlyMarked);
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      JComponent.WHEN_FOCUSED
    );

    final SpeedSearchBase<Table> speedSearch = new SpeedSearchBase<Table>(myTable) {
      public int getSelectedIndex() {
        return myTable.getSelectedRow();
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
            myTable.getSelectionModel().setSelectionInterval(row, row);
            TableUtil.scrollSelectionToVisible(myTable);
            break;
          }
        }
      }
    };
    speedSearch.setComparator(new SpeedSearchBase.SpeedSearchComparator(false));
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

  public void addModule(Module element, final boolean isMarked, boolean isStoreGeneratedSourcesUnderContent) {
    myTableModel.addElement(element, isMarked, isStoreGeneratedSourcesUnderContent);
    selectRow(myTableModel.getRowCount() - 1);
    myTable.requestFocus();
  }

  public boolean isElementMarked(Module element) {
    final int elementRow = myTableModel.getElementRow(element);
    return myTableModel.isMarked(elementRow);
  }

  public void setElementMarked(Module element, boolean marked) {
    final int elementRow = myTableModel.getElementRow(element);
    myTableModel.updateBooleanMap(elementRow, marked, myTableModel.myMarkedMap);
  }

  public void setStoreGeneratedSourcesUnderContent(Module element, boolean value) {
    final int elementRow = myTableModel.getElementRow(element);
    myTableModel.updateBooleanMap(elementRow, value, myTableModel.myStoreUnderContentMap);
  }

  public void removeElement(Module element) {
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
    final List<Module> elements = new ArrayList<Module>();
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

  public List<Pair<Module, Boolean>> getMarkedModules() {
    final int count = myTableModel.getRowCount();
    List<Pair<Module, Boolean>> elements = new ArrayList<Pair<Module, Boolean>>();
    for (int idx = 0; idx < count; idx++) {
      final Module module = myTableModel.getModuleAt(idx);
      if (myTableModel.isMarked(idx)) {
        elements.add(new Pair<Module, Boolean>(module, myTableModel.isGenerateSourcesToContent(module)));
      }
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

  private final class MyTableModel extends AbstractTableModel {
    private final List<Module> myElements = new ArrayList<Module>();
    private final Map<Module, Boolean> myMarkedMap = new HashMap<Module, Boolean>();
    private final Map<Module, Boolean> myStoreUnderContentMap = new HashMap<Module, Boolean>();
    public final int CHECK_MARK_COLUM_INDEX = 0;
    public final int ELEMENT_COLUMN_INDEX = 1;
    public final int STORE_UNDER_CONTENT_COLUM_INDEX = 2;

    public void sort(Comparator<Module> comparator) {
      Collections.sort(myElements, comparator);
      fireTableDataChanged();
    }

    public Module getModuleAt(int index) {
      return myElements.get(index);
    }

    public boolean isMarked(int index) {
      final Module element = myElements.get(index);
      return myMarkedMap.get(element).booleanValue();
    }

    public boolean isGenerateSourcesToContent(int index) {
      final Module element = myElements.get(index);
      return myStoreUnderContentMap.get(element).booleanValue();
    }
    
    public boolean isGenerateSourcesToContent(Module module) {
      final Boolean value = myStoreUnderContentMap.get(module);
      return value != null && value.booleanValue();
    }

    void addElement(Module element, boolean isMarked, boolean isStoreGeneratedSourcesUnderContent) {
      myElements.add(element);
      myMarkedMap.put(element, Boolean.valueOf(isMarked));
      myStoreUnderContentMap.put(element, Boolean.valueOf(isStoreGeneratedSourcesUnderContent));
      int row = myElements.size() - 1;
      fireTableRowsInserted(row, row);
    }

    void addElements(List<Module> elements, boolean isMarked) {
      if (elements == null || elements.size() == 0) {
        return;
      }
      for (final Module element : elements) {
        myElements.add(element);
        myMarkedMap.put(element, isMarked ? Boolean.TRUE : Boolean.FALSE);
        myStoreUnderContentMap.put(element, Boolean.FALSE);
      }
      fireTableRowsInserted(myElements.size() - elements.size(), myElements.size() - 1);
    }

    public void removeElement(Module element) {
      final boolean reallyRemoved = myElements.remove(element);
      if (reallyRemoved) {
        myMarkedMap.remove(element);
        myStoreUnderContentMap.remove(element);
        fireTableDataChanged();
      }
    }

    public void changeElementRow(Module element, int row) {
      final boolean reallyRemoved = myElements.remove(element);
      if (reallyRemoved) {
        myElements.add(row, element);
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

    public void removeRows(int[] rows) {
      final List<Module> toRemove = new ArrayList<Module>();
      for (int row : rows) {
        final Module element = myElements.get(row);
        toRemove.add(element);
        myMarkedMap.remove(element);
        myStoreUnderContentMap.remove(element);
      }
      myElements.removeAll(toRemove);
      fireTableDataChanged();
    }

    public int getRowCount() {
      return myElements.size();
    }

    public int getColumnCount() {
      return 3;
    }

    @Nullable
    public Object getValueAt(int rowIndex, int columnIndex) {
      Module element = myElements.get(rowIndex);
      if (columnIndex == ELEMENT_COLUMN_INDEX) {
        return element;
      }
      if (columnIndex == CHECK_MARK_COLUM_INDEX) {
        return myMarkedMap.get(element);
      }
      if (columnIndex == STORE_UNDER_CONTENT_COLUM_INDEX) {
        return myStoreUnderContentMap.get(element);
      }
      return null;
    }

    public void setValueAt(Object value, int rowIndex, int columnIndex) {
      if (columnIndex == CHECK_MARK_COLUM_INDEX) {
        updateBooleanMap(rowIndex, ((Boolean)value).booleanValue(), myMarkedMap);
      }
      else if (columnIndex == STORE_UNDER_CONTENT_COLUM_INDEX) {
        updateBooleanMap(rowIndex, ((Boolean)value).booleanValue(), myStoreUnderContentMap);
      }
    }

    private void updateBooleanMap(int rowIndex, boolean marked, final Map<Module, Boolean> map) {
      final Module element = myElements.get(rowIndex);
      final Boolean newValue = marked? Boolean.TRUE : Boolean.FALSE;
      map.put(element, newValue);
      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    private void setMarked(int[] rows, final boolean marked) {
      if (rows == null || rows.length == 0) {
        return;
      }
      int firstRow = Integer.MAX_VALUE;
      int lastRow = Integer.MIN_VALUE;
      final Boolean newValue = marked? Boolean.TRUE : Boolean.FALSE;
      for (final int row : rows) {
        final Module element = myElements.get(row);
        myMarkedMap.put(element, newValue);
        firstRow = Math.min(firstRow, row);
        lastRow = Math.max(lastRow, row);
      }
      fireTableRowsUpdated(firstRow, lastRow);
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECK_MARK_COLUM_INDEX || columnIndex == STORE_UNDER_CONTENT_COLUM_INDEX) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (!ProcessedModulesChooser.this.isEnabled()) {
        return false;
      }
      if (columnIndex == CHECK_MARK_COLUM_INDEX) {
        return true;
      }
      if (columnIndex == STORE_UNDER_CONTENT_COLUM_INDEX) {
        return isMarked(rowIndex);
      }
      return false;
    }

    public void clear() {
      myElements.clear();
      myMarkedMap.clear();
      myStoreUnderContentMap.clear();
      fireTableDataChanged();
    }
  }

  private class MyElementColumnCellRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final Color color = UIUtil.getTableFocusCellBackground();
      Component component;
      Module module = (Module)value;
      try {
        UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, table.getSelectionBackground());
        component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setText(module != null ? module.getName() + " (" + FileUtil.toSystemDependentName(module.getModuleFilePath()) + ")" : "");
        if (component instanceof JLabel) {
          ((JLabel)component).setBorder(noFocusBorder);
        }
      }
      finally {
        UIManager.put(UIUtil.TABLE_FOCUS_CELL_BACKGROUND_PROPERTY, color);
      }
      final MyTableModel model = (MyTableModel)table.getModel();
      component.setEnabled(ProcessedModulesChooser.this.isEnabled() && (myColorUnmarkedElements? model.isMarked(row) : true));
      if (component instanceof JLabel) {
        final Icon icon = module != null ? module.getModuleType().getNodeIcon(false) : null;
        JLabel label = (JLabel)component;
        label.setIcon(icon);
        label.setDisabledIcon(icon);
      }
      component.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
      return component;
    }
  }

  private class CheckMarkColumnCellRenderer implements TableCellRenderer {
    private final TableCellRenderer myDelegate;

    public CheckMarkColumnCellRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component component = myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      component.setEnabled(ProcessedModulesChooser.this.isEnabled());
      if (component instanceof JComponent) {
        ((JComponent)component).setBorder(null);
      }
      return component;
    }
  }
}