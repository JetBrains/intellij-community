package com.intellij.database.datagrid;

public interface GridColumnLayout<Row, Column> {
  boolean resetLayout();

  void newColumnsAdded(ModelIndexSet<Column> columnIndices);

  void newRowsAdded(ModelIndexSet<Row> rowIndices);

  void columnsShown(ModelIndexSet<?> columnDataIndices);

  void invalidateCache();
}
