// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.configurable;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.todo.TodoFilter;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.ui.ItemRemovable;
import one.util.streamex.StreamEx;

import javax.swing.table.AbstractTableModel;
import java.util.List;

final class FiltersTableModel extends AbstractTableModel implements ItemRemovable {
  private final String[] myColumnNames = {IdeBundle.message("column.todo.filters.name"), IdeBundle.message("column.todo.filter.patterns")};
  private final List<TodoFilter> myFilters;

  FiltersTableModel(List<TodoFilter> filters) {
    myFilters = filters;
  }

  @Override
  public String getColumnName(int column) {
    return myColumnNames[column];
  }

  @Override
  public Class<?> getColumnClass(int column) {
    return switch (column) {
      case 0, 1 -> String.class;
      default -> throw new IllegalArgumentException();
    };
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public int getRowCount() {
    return myFilters.size();
  }

  @Override
  public Object getValueAt(int row, int column) {
    var filter = myFilters.get(row);
    return switch (column) {
      case 0 -> filter.getName();
      case 1 -> StreamEx.of(filter.iterator()).map(TodoPattern::getPatternString).joining(" | ");
      default -> throw new IllegalArgumentException();
    };
  }

  @Override
  public void removeRow(int index) {
    myFilters.remove(index);
    fireTableRowsDeleted(index, index);
  }
}
