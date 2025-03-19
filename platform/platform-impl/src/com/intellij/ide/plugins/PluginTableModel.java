// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * @author stathik
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public abstract class PluginTableModel extends AbstractTableModel implements SortableColumnModel {
  protected static final String NAME = "Name";

  protected ColumnInfo[] columns;
  protected final List<IdeaPluginDescriptor> view = new ArrayList<>();

  protected PluginTableModel() { }

  @Override
  public int getColumnCount() {
    return columns.length;
  }

  @Override
  public ColumnInfo[] getColumnInfos() {
    return columns;
  }

  @Override
  public boolean isSortable() {
    return true;
  }

  @Override
  public void setSortable(boolean aBoolean) {
    // do nothing cause it's always sortable
  }

  @Override
  public String getColumnName(int column) {
    return columns[column].getName();
  }

  public IdeaPluginDescriptor getObjectAt (int row) {
    return view.get(row);
  }

  @Override
  public Object getRowValue(int row) {
    return getObjectAt(row);
  }

  @Override
  public RowSorter.SortKey getDefaultSortKey() {
    return null;
  }

  @Override
  public int getRowCount() {
    return view.size();
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return columns[columnIndex].valueOf(getObjectAt(rowIndex));
  }

  @Override
  public boolean isCellEditable(final int rowIndex, final int columnIndex) {
    return columns[columnIndex].isCellEditable(getObjectAt(rowIndex));
  }

  @Override
  public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
    columns[columnIndex].setValue(getObjectAt(rowIndex), aValue);
    fireTableCellUpdated(rowIndex, columnIndex);
  }

  public abstract int getNameColumn();
}
