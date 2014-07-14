/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
abstract public class PluginTableModel extends AbstractTableModel implements SortableColumnModel {
  protected static final String NAME = "Name";
  protected ColumnInfo[] columns;
  protected List<IdeaPluginDescriptor> view;
  private RowSorter.SortKey myDefaultSortKey;
  protected final List<IdeaPluginDescriptor> filtered = new ArrayList<IdeaPluginDescriptor>();
  private boolean mySortByStatus;
  private boolean mySortByRating;
  private boolean mySortByDownloads;
  private boolean mySortByUpdated;

  protected PluginTableModel() {
  }

  public PluginTableModel(ColumnInfo... columns) {
    this.columns = columns;
  }

  public void setSortKey(final RowSorter.SortKey sortKey) {
    myDefaultSortKey = sortKey;
  }

  public int getColumnCount() {
    return columns.length;
  }

  public ColumnInfo[] getColumnInfos() {
    return columns;
  }

  public boolean isSortable() {
    return true;
  }

  public void setSortable(boolean aBoolean) {
    // do nothing cause it's always sortable
  }

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

  public int getRowCount() {
    return view.size();
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    return columns[columnIndex].valueOf(getObjectAt(rowIndex));
  }

  public boolean isCellEditable(final int rowIndex, final int columnIndex) {
    return columns[columnIndex].isCellEditable(getObjectAt(rowIndex));
  }

  public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
    columns[columnIndex].setValue(getObjectAt(rowIndex), aValue);
    fireTableCellUpdated(rowIndex, columnIndex);
  }

  public ArrayList<IdeaPluginDescriptorImpl> dependent(IdeaPluginDescriptorImpl plugin) {
    ArrayList<IdeaPluginDescriptorImpl> list = new ArrayList<IdeaPluginDescriptorImpl>();
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

  public void filter(List<IdeaPluginDescriptor> filtered){
    fireTableDataChanged();
  }

  protected void filter(String filter) {
    final SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final Set<String> search = optionsRegistrar.getProcessedWords(filter);

    final ArrayList<IdeaPluginDescriptor> desc = new ArrayList<IdeaPluginDescriptor>();

    final List<IdeaPluginDescriptor> toProcess = toProcess();
    for (IdeaPluginDescriptor descriptor : filtered) {
      if (!toProcess.contains(descriptor)) {
        toProcess.add(descriptor);
      }
    }
    filtered.clear();
    for (IdeaPluginDescriptor descriptor : toProcess) {
      if (isPluginDescriptorAccepted(descriptor) &&
          PluginManagerMain.isAccepted(filter, search, descriptor)) {
        desc.add(descriptor);
      }
      else {
        filtered.add(descriptor);
      }
    }
    filter(desc);
  }

  protected ArrayList<IdeaPluginDescriptor> toProcess() {
    return new ArrayList<IdeaPluginDescriptor>(view);
  }

  public abstract int getNameColumn();

  public abstract boolean isPluginDescriptorAccepted(IdeaPluginDescriptor descriptor);

  public void sort() {
    Collections.sort(view, columns[getNameColumn()].getComparator());
    fireTableDataChanged();
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
    final ArrayList<IdeaPluginDescriptor> list = new ArrayList<IdeaPluginDescriptor>();
    list.addAll(view);
    list.addAll(filtered);
    return list;
  }
}
