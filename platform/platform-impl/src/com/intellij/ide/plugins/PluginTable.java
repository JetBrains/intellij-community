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
package com.intellij.ide.plugins;

import com.intellij.ui.TableUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 11, 2003
 * Time: 4:19:20 PM
 * To change this template use Options | File Templates.
 */
public class PluginTable extends JBTable {
  public PluginTable(final PluginTableModel model) {
    super(model);
    getColumnModel().setColumnMargin(0);
    for (int i = 0; i < model.getColumnCount(); i++) {
      TableColumn column = getColumnModel().getColumn(i);
      final ColumnInfo columnInfo = model.getColumnInfos()[i];
      column.setCellEditor(columnInfo.getEditor(null));
      if (columnInfo.getColumnClass() == Boolean.class) {
        final int width = new JCheckBox().getPreferredSize().width;
        column.setWidth(width);
        column.setPreferredWidth(width);
        column.setMaxWidth(width);
        column.setMinWidth(width);
      }
    }

    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setShowGrid(false);
    setStriped(true);
  }

  public void setColumnWidth(final int columnIndex, final int width) {
    TableColumn column = getColumnModel().getColumn(columnIndex);
    column.setMinWidth(width);
    column.setMaxWidth(width);
  }

  @Override
  protected boolean isSortOnUpdates() {
    return false;
  }

  public void setValueAt(final Object aValue, final int row, final int column) {
    super.setValueAt(aValue, row, column);
    repaint(); //in order to update invalid plugins
  }

  public TableCellRenderer getCellRenderer(final int row, final int column) {
    final ColumnInfo columnInfo = ((PluginTableModel)getModel()).getColumnInfos()[column];
    return columnInfo.getRenderer(getObjectAt(row));
  }

  public Object[] getElements() {
    return ((PluginTableModel)getModel()).view.toArray();
  }

  public IdeaPluginDescriptor getObjectAt(int row) {
    return ((PluginTableModel)getModel()).getObjectAt(convertRowIndexToModel(row));
  }

  public void select(IdeaPluginDescriptor... descriptors) {
    PluginTableModel tableModel = (PluginTableModel)getModel();
    getSelectionModel().clearSelection();
    for (int i=0; i<tableModel.getRowCount();i++) {
      IdeaPluginDescriptor descriptorAt = tableModel.getObjectAt(i);
      if (ArrayUtil.find(descriptors,descriptorAt) != -1) {
        final int row = convertRowIndexToView(i);
        getSelectionModel().addSelectionInterval(row, row);
      }
    }
    TableUtil.scrollSelectionToVisible(this);
  }

  public IdeaPluginDescriptor getSelectedObject() {                      
    IdeaPluginDescriptor selected = null;
    if (getSelectedRowCount() > 0) {
      selected = getObjectAt(getSelectedRow());
    }
    return selected;
  }

  public IdeaPluginDescriptor[] getSelectedObjects() {
    IdeaPluginDescriptor[] selection = null;
    if (getSelectedRowCount() > 0) {
      int[] poses = getSelectedRows();
      selection = new IdeaPluginDescriptor[poses.length];
      for (int i = 0; i < poses.length; i++) {
        selection[i] = getObjectAt(poses[i]);
      }
    }
    return selection;
  }
}
