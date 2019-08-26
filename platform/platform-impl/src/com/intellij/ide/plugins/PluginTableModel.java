// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.io.IOException;
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
  protected final List<IdeaPluginDescriptor> view = new ArrayList<>();
  protected final List<IdeaPluginDescriptor> filtered = new ArrayList<>();

  private RowSorter.SortKey myDefaultSortKey;

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

  @NotNull
  public List<IdeaPluginDescriptorImpl> dependent(@NotNull IdeaPluginDescriptorImpl plugin) {
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
  public List<IdeaPluginDescriptor> getAllPlugins() {
    List<IdeaPluginDescriptor> list = new ArrayList<>(view.size() + filtered.size());
    list.addAll(view);
    list.addAll(filtered);
    return list;
  }

  public List<IdeaPluginDescriptor> getAllRepoPlugins() {
    try {
      List<IdeaPluginDescriptor> list = RepositoryHelper.loadCachedPlugins();
      if (list != null) {
        return list;
      }
    }
    catch (IOException ignored) {
    }
    return Collections.emptyList();
  }
}
