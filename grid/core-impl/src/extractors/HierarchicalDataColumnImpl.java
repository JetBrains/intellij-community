package com.intellij.database.extractors;

import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.ExtractorHierarchicalGridColumn;
import org.jetbrains.annotations.NotNull;

public class HierarchicalDataColumnImpl extends DataColumnImpl {
  public HierarchicalDataColumnImpl(
    @NotNull ExtractorHierarchicalGridColumn rootColumn
  ) {
    super(rootColumn);
  }

  public boolean isMatchesSelection() {
    return ((ExtractorHierarchicalGridColumn) getColumn()).isMatchesSelection();
  }
}
