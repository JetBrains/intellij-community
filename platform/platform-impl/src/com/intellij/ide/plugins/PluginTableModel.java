/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author stathik
 * @author Konstantin Bulenkov
 */
public abstract class PluginTableModel extends AbstractTableModel implements SortableColumnModel {
  protected static final String NAME = "Name";

  protected ColumnInfo[] columns;
  protected final List<IdeaPluginDescriptor> view = ContainerUtil.newArrayList();
  protected final List<IdeaPluginDescriptor> filtered = ContainerUtil.newArrayList();

  private RowSorter.SortKey myDefaultSortKey;
  private boolean mySortByStatus;
  private boolean mySortByRating;
  private boolean mySortByDownloads;
  private boolean mySortByUpdated;

  protected PluginTableModel() { }

  public void setSortKey(final RowSorter.SortKey sortKey) {
    myDefaultSortKey = sortKey;
  }

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
    return myDefaultSortKey;
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

  public List<IdeaPluginDescriptorImpl> dependent(IdeaPluginDescriptorImpl plugin) {
    List<IdeaPluginDescriptorImpl> list = new ArrayList<>();
    for (IdeaPluginDescriptor any : getAllPlugins()) {
      if (any instanceof IdeaPluginDescriptorImpl) {
        PluginId[] dep = any.getDependentPluginIds();
        for (PluginId id : dep) {
          if (id == plugin.getPluginId()) {
            list.add((IdeaPluginDescriptorImpl)any);
            break;
          }
        }
      }
    }
    return list;
  }

  public abstract void updatePluginsList(List<IdeaPluginDescriptor> list);

  protected void filter(String filter) {
    Set<String> search = SearchableOptionsRegistrar.getInstance().getProcessedWords(filter);
    List<IdeaPluginDescriptor> allPlugins = getAllPlugins();

    view.clear();
    filtered.clear();

    for (IdeaPluginDescriptor descriptor : allPlugins) {
      if (isPluginDescriptorAccepted(descriptor) && PluginManagerMain.isAccepted(filter, search, descriptor)) {
        view.add(descriptor);
      }
      else {
        filtered.add(descriptor);
      }
    }

    fireTableDataChanged();
  }

  public abstract int getNameColumn();

  public abstract boolean isPluginDescriptorAccepted(IdeaPluginDescriptor descriptor);

  public void sort() {
    try {
      Collections.sort(view, columns[getNameColumn()].getComparator());
      fireTableDataChanged();
    }
    catch (IllegalArgumentException e) {
      String message = e.getMessage();
      if (message != null && e.getMessage().contains("Comparison method violates its general contract")) {
        ColumnInfo column = columns[getNameColumn()];
        e = new IllegalArgumentException("model=" + this + " col=" + column + " cmp=" + column.getComparator(), e);
      }
      throw e;
    }
  }

  public boolean isSortByStatus() {
    return mySortByStatus;
  }

  public void setSortByStatus(boolean sortByStatus) {
    mySortByStatus = sortByStatus;
  }

  public boolean isSortByRating() {
    return mySortByRating;
  }

  public void setSortByRating(boolean sortByRating) {
    mySortByRating = sortByRating;
  }

  public boolean isSortByDownloads() {
    return mySortByDownloads;
  }

  public void setSortByDownloads(boolean sortByDownloads) {
    mySortByDownloads = sortByDownloads;
  }

  public boolean isSortByUpdated() {
    return mySortByUpdated;
  }

  public void setSortByUpdated(boolean sortByUpdated) {
    mySortByUpdated = sortByUpdated;
  }

  public List<IdeaPluginDescriptor> getAllPlugins() {
    List<IdeaPluginDescriptor> list = ContainerUtil.newArrayListWithCapacity(view.size() + filtered.size());
    list.addAll(view);
    list.addAll(filtered);
    return list;
  }
}
