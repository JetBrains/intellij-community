/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.util.importProject;

import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class DetectedRootsChooser {
  private static final int CHECKBOX_COLUMN_WIDTH = new JCheckBox().getPreferredSize().width + 4;
  private final ColumnInfo<DetectedRootData,Boolean> myIncludedColumn = new ColumnInfo<DetectedRootData, Boolean>("") {

    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public Boolean valueOf(DetectedRootData detectedRootData) {
      return detectedRootData.isIncluded();
    }

    @Override
    public boolean isCellEditable(DetectedRootData detectedRootData) {
      return true;
    }

    @Override
    public int getWidth(JTable table) {
      return CHECKBOX_COLUMN_WIDTH;
    }

    @Override
    public void setValue(DetectedRootData detectedRootData, Boolean value) {
      if (value.booleanValue() != detectedRootData.isIncluded()) {
        detectedRootData.setIncluded(value);
        myDispatcher.getMulticaster().selectionChanged();
      }
    }
  };
  private static final ColumnInfo<DetectedRootData, String> ROOT_COLUMN = new ColumnInfo<DetectedRootData, String>("") {
    @Override
    public String valueOf(DetectedRootData detectedRootData) {
      return detectedRootData.getDirectory().getAbsolutePath();
    }
  };
  private static final ColumnInfo<DetectedRootData, DetectedProjectRoot> ROOT_TYPE_COLUMN = new ColumnInfo<DetectedRootData, DetectedProjectRoot>("") {
    @Override
    public DetectedProjectRoot valueOf(DetectedRootData detectedRootData) {
      return detectedRootData.getSelectedRoot();
    }

    @Override
    public TableCellRenderer getRenderer(DetectedRootData detectedRootData) {
      if (isCellEditable(detectedRootData)) {
        return new ComboBoxTableRenderer<DetectedProjectRoot>(detectedRootData.getAllRoots()) {
          @Override
          protected String getTextFor(@NotNull DetectedProjectRoot value) {
            return value.getRootTypeName();
          }
        };
      }
      return new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
          final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          if (value instanceof DetectedProjectRoot) {
            setText(((DetectedProjectRoot)value).getRootTypeName());
          }
          return component;
        }
      };
    }

    @Override
    public TableCellEditor getEditor(DetectedRootData o) {
      final ComboBox comboBox = new ComboBox(new CollectionComboBoxModel(Arrays.asList(o.getAllRoots()), o.getSelectedRoot()));
      comboBox.setRenderer(new ListCellRendererWrapper<DetectedProjectRoot>() {
        @Override
        public void customize(JList list, DetectedProjectRoot value, int index, boolean selected, boolean hasFocus) {
          setText(value.getRootTypeName());
        }
      });
      return new DefaultCellEditor(comboBox);
    }

    @Override
    public boolean isCellEditable(DetectedRootData detectedRootData) {
      return detectedRootData.getAllRoots().length > 1;
    }

    @Override
    public void setValue(DetectedRootData detectedRootData, DetectedProjectRoot value) {
      detectedRootData.setSelectedRoot(value);
    }
  };
  private TableView<DetectedRootData> myTable;
  private JComponent myComponent;
  private final ListTableModel<DetectedRootData> myModel;
  private final EventDispatcher<RootSelectionListener> myDispatcher = EventDispatcher.create(RootSelectionListener.class);

  public DetectedRootsChooser() {
    myModel = new ListTableModel<>();
    myTable = new TableView<>(myModel);
    myTable.setTableHeader(null);
    myTable.setShowGrid(false);
    myComponent = ScrollPaneFactory.createScrollPane(myTable);
    myTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          invertSelectedRows();
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      JComponent.WHEN_FOCUSED
    );
  }

  private void invertSelectedRows() {
    final int[] selectedRows = myTable.getSelectedRows();
    if (selectedRows.length == 0) return;

    boolean included = false;
    for (int selectedRow : selectedRows) {
      included |= myModel.getItems().get(selectedRow).isIncluded();
    }
    int first = Integer.MAX_VALUE;
    int last = -1;
    for (int selectedRow : selectedRows) {
      first = Math.min(first, selectedRow);
      last = Math.max(last, selectedRow);
      myModel.getItems().get(selectedRow).setIncluded(!included);
    }
    myModel.fireTableRowsUpdated(first, last+1);
    myDispatcher.getMulticaster().selectionChanged();
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void addSelectionListener(RootSelectionListener listener) {
    myDispatcher.addListener(listener);
  }

  public void setAllElementsMarked(boolean mark) {
    for (DetectedRootData data : myModel.getItems()) {
      data.setIncluded(mark);
    }
    myModel.fireTableRowsUpdated(0, myModel.getRowCount()-1);
    myDispatcher.getMulticaster().selectionChanged();
  }

  public List<DetectedRootData> getMarkedElements() {
    final List<DetectedRootData> result = new ArrayList<>();
    for (DetectedRootData data : myModel.getItems()) {
      if (data.isIncluded()) {
        result.add(data);
      }
    }
    return result;
  }

  public void setElements(List<? extends DetectedRootData> roots) {
    Set<String> rootTypes = new HashSet<>();
    for (DetectedRootData root : roots) {
      for (DetectedProjectRoot projectRoot : root.getAllRoots()) {
        rootTypes.add(projectRoot.getRootTypeName());
      }
    }
    myModel.setColumnInfos(new ColumnInfo[]{myIncludedColumn, ROOT_COLUMN, ROOT_TYPE_COLUMN});
    int max = 0;
    for (String rootType : rootTypes) {
      max = Math.max(max, myTable.getFontMetrics(myTable.getFont()).stringWidth(rootType));
    }
    final TableColumn column = myTable.getColumnModel().getColumn(2);
    int width = max + 20;//add space for combobox button
    column.setPreferredWidth(width);
    column.setMaxWidth(width);
    myTable.updateColumnSizes();
    List<DetectedRootData> sortedRoots = new ArrayList<>(roots);
    Collections.sort(sortedRoots, (o1, o2) -> o1.getDirectory().compareTo(o2.getDirectory()));
    myModel.setItems(sortedRoots);
  }

  interface RootSelectionListener extends EventListener {
    void selectionChanged();
  }
}
