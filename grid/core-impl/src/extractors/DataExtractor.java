package com.intellij.database.extractors;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.util.Out;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.List;

/**
 * @author gregsh
 */
public interface DataExtractor {

  @NotNull
  String getFileExtension();

  boolean supportsText();

  Extraction startExtraction(
    @NotNull Out out,
    @NotNull List<? extends GridColumn> allColumns,
    @NotNull String query,
    @NotNull ExtractionConfig config,
    int... selectedColumns
  );

  interface Extraction {

    void updateColumns(GridColumn @NotNull [] columns);

    void addData(List<? extends GridRow> rows);

    void complete();

    default void completeBatch() {
    }
  }
}
