package com.intellij.database.extractors;

import com.intellij.database.datagrid.DataConsumer;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.util.Out;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public abstract class TranspositionAwareExtractor extends DefaultValuesExtractor {
  protected static final GridColumn ROW_NAMES_COLUMN = new DataConsumer.Column(0, "Column", Types.VARCHAR, "", "");

  protected TranspositionAwareExtractor(@NotNull ObjectFormatter converter) {
    super(converter);
  }

  @Override
  public String getValueLiteral(@NotNull String value,
                                @Nullable GridRow row,
                                @Nullable GridColumn column) {
    return super.getValueLiteral(value, getOriginalRow(row, column), getOriginalColumn(row, column));
  }

  @Override
  protected boolean isStringLiteral(@Nullable GridRow row, @Nullable GridColumn column) {
    return false;
  }

  @Override
  protected @Nullable String getValueAsString(@NotNull GridRow row, @NotNull GridColumn column, @NotNull ObjectFormatterMode mode) {
    //noinspection ConstantConditions
    return super.getValueAsString(getOriginalRow(row, column), getOriginalColumn(row, column), mode);
  }

  protected static @Nullable GridColumn getOriginalColumn(@Nullable GridRow row, @Nullable GridColumn column) {
    return row instanceof TransposedRow ? ((TransposedRow)row).originalColumn : column;
  }

  protected static @Nullable GridRow getOriginalRow(@Nullable GridRow row, @Nullable GridColumn column) {
    return column instanceof TransposedColumn ? ((TransposedColumn)column).originalRow : row;
  }

  public abstract static class TranspositionAwareExtraction extends DefaultExtraction {
    protected final DataExtractor myExtractor;

    public TranspositionAwareExtraction(Out out, @NotNull ExtractionConfig config,
                                        List<? extends GridColumn> allColumns, String query, int[] selectedColumnIndices,
                                        DataExtractor extractor) {
      super(out, config, allColumns, query, selectedColumnIndices);
      myExtractor = extractor;
    }

    @Override
    protected final void appendHeader(boolean appendNewLine) {
      if (!myConfig.isTransposed()) {
        doAppendHeader(appendNewLine);
      }
    }

    @Override
    protected final void appendData(List<? extends GridRow> rows) {
      if (!myConfig.isTransposed()) {
        doAppendData(rows);
      }
      else {
        doTransposedExtraction(rows);
      }
    }

    @Override
    protected final void appendFooter() {
      if (!myConfig.isTransposed()) {
        doAppendFooter();
      }
    }

    protected @NotNull String getRowNumber(GridRow row) {
      if (!myAllColumns.isEmpty() && ROW_NAMES_COLUMN == myAllColumns.get(0)) {
        Object rowNumber = ROW_NAMES_COLUMN.getValue(row);
        return rowNumber instanceof String ? (String)rowNumber : "";
      }
      return String.valueOf(row.getRowNum());
    }

    @Override
    protected @NotNull GridColumn getRowNumbersColumn() {
      return !myAllColumns.isEmpty() && ROW_NAMES_COLUMN == myAllColumns.get(0) ? ROW_NAMES_COLUMN : super.getRowNumbersColumn();
    }

    protected void doTransposedExtraction(List<? extends GridRow> rows) {
      List<GridColumn> transposedColumns = getTransposedColumns(rows);
      int[] selectedColumns = new int[Math.max(0, transposedColumns.size() - 1)];
      // skip row numbers column
      for (int i = 0; i < selectedColumns.length; i++) {
        selectedColumns[i] = i + 1;
      }

      Extraction e = spawnChildExtraction(
        myOut,
        transposedColumns,
        myQuery,
        myConfig.toBuilder().setTransposed(false).build(),
        myConfig.isTransposed(),
        selectedColumns
      );
      e.addData(getTransposedRows(rows));
      e.completeBatch();
    }

    protected Extraction spawnChildExtraction(@NotNull Out out,
                                              @NotNull List<? extends GridColumn> allColumns,
                                              @NotNull String query,
                                              @NotNull ExtractionConfig config,
                                              Boolean isOriginallyTransposed,
                                              int... selectedColumns) {
      return myExtractor.startExtraction(out, allColumns, query, config, selectedColumns);
    }

    protected abstract void doAppendHeader(boolean appendNewLine);

    protected abstract void doAppendData(List<? extends GridRow> rows);

    protected void doAppendFooter() {
      super.appendFooter();
    }

    protected List<GridRow> getTransposedRows(List<? extends GridRow> rows) {
      int[] originalSelection = GridExtractorsUtilCore.getNonEmptySelection(myAllColumns, mySelectedColumnIndices);
      List<GridRow> transposedRows = new ArrayList<>(originalSelection.length);
      for (int i : originalSelection) {
        GridColumn column = myAllColumns.get(i);
        Object[] transposedRow = new Object[rows.size() + 1];
        transposedRow[0] = column.getName();
        for (int j = 0; j < rows.size(); j++) {
          transposedRow[j + 1] = column.getValue(rows.get(j));
        }
        transposedRows.add(new TransposedRow(i, transposedRow, column));
      }
      return transposedRows;
    }

    protected List<GridColumn> getTransposedColumns(List<? extends GridRow> rows) {
      List<GridColumn> transposedColumns = new ArrayList<>(rows.size() + 1);
      transposedColumns.add(ROW_NAMES_COLUMN);
      for (int i = 0; i < rows.size(); i++) {
        transposedColumns.add(new TransposedColumn(i + 1, rows.get(i)));
      }
      return transposedColumns;
    }
  }

  protected static class TransposedRow extends DataConsumer.Row {
    public final GridColumn originalColumn;

    public TransposedRow(int rowNum, Object[] values, GridColumn originalColumn) {
      super(rowNum, values);
      this.originalColumn = originalColumn;
    }
  }

  protected static class TransposedColumn extends DataConsumer.Column {
    public final GridRow originalRow;

    public TransposedColumn(int columnNum, GridRow originalRow) {
      super(columnNum, "Value (" + originalRow.getRowNum() + ")", Types.VARCHAR, "", "");
      this.originalRow = originalRow;
    }
  }
}
