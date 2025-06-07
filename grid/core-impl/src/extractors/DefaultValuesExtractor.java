package com.intellij.database.extractors;

import com.intellij.database.datagrid.DataConsumer;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.util.Out;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Types;
import java.util.Arrays;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public abstract class DefaultValuesExtractor implements DataExtractor {

  protected final ObjectFormatter myConverter;

  public DefaultValuesExtractor(@NotNull ObjectFormatter converter) {
    myConverter = converter;
  }

  public String getLineSeparator() {
    return "\n";
  }

  public @NotNull String getValueLiteral(@NotNull GridRow row,
                                         @NotNull GridColumn column,
                                         @NotNull ObjectFormatterConfig config) {
    String value = getValueAsString(row, column, config);
    return value == null ? getNullLiteral(row, column) : getValueLiteral(value, row, column);
  }

  protected @Nullable String getValueAsString(@NotNull GridRow row,
                                              @NotNull GridColumn column,
                                              @NotNull ObjectFormatterConfig config) {
    return myConverter.objectToString(getValue(row, column), column, config);
  }

  public @NotNull String getValueLiteral(@NotNull GridRow row,
                                         @NotNull GridColumn column,
                                         @NotNull ObjectFormatterMode mode) {
    String value = getValueAsString(row, column, mode);
    return value == null ? getNullLiteral(row, column) : getValueLiteral(value, row, column);
  }

  protected @Nullable String getValueAsString(@NotNull GridRow row,
                                              @NotNull GridColumn column,
                                              @NotNull ObjectFormatterMode mode) {
    return myConverter.objectToString(getValue(row, column), column, DatabaseObjectFormatterConfig.get(mode));
  }

  protected @Nullable Object getValue(@NotNull GridRow row, @NotNull GridColumn column) {
    return column.getValue(row);
  }

  public String getValueLiteral(@NotNull String value,
                                @Nullable GridRow row,
                                @Nullable GridColumn column) {
    return isStringLiteral(row, column) ? getStringValueLiteral(column, value) : value;
  }

  protected abstract boolean isStringLiteral(@Nullable GridRow row, @Nullable GridColumn column);

  @Override
  public abstract Extraction startExtraction(@NotNull Out out,
                                             @NotNull List<? extends GridColumn> allColumns,
                                             @NotNull String query,
                                             @NotNull ExtractionConfig config,
                                             int... selectedColumns);

  public String getColumnName(GridColumn column) {
    return column.getName();
  }

  @Override
  public @NotNull String getFileExtension() {
    return "txt";
  }

  public @NotNull String getNullLiteral(GridRow row, GridColumn column) {
    return "NULL";
  }

  public @NotNull String getStringValueLiteral(@Nullable GridColumn column, @NotNull String value) {
    return value;
  }

  protected abstract static class DefaultExtraction implements Extraction {
    protected final Out myOut;
    protected List<? extends GridColumn> myAllColumns;
    protected String myQuery;
    protected final int[] mySelectedColumnIndices;
    protected final ExtractionConfig myConfig;

    protected boolean myHeaderAppended;
    protected boolean myFooterAppended;


    public DefaultExtraction(Out out, ExtractionConfig config,
                             List<? extends GridColumn> allColumns, String query, int[] selectedColumnIndices) {
      myOut = out;
      myConfig = config;
      myAllColumns = allColumns;
      myQuery = query;
      mySelectedColumnIndices = selectedColumnIndices;
    }

    @Override
    public void updateColumns(GridColumn @NotNull [] columns) {
      myAllColumns = Arrays.asList(columns);
    }

    @Override
    public void addData(List<? extends GridRow> rows) {
      if (!myHeaderAppended) {
        appendHeader(!rows.isEmpty());
        myHeaderAppended = true;
      }
      appendData(rows);
    }

    @Override
    public void completeBatch() {
      if (!myHeaderAppended) {
        appendHeader(false);
        myHeaderAppended = true;
      }
      if (!myFooterAppended) {
        appendFooter();
        myFooterAppended = true;
      }
    }

    @Override
    public void complete() {
      completeBatch();
    }

    protected void appendHeader(boolean appendNewLine) { }

    protected void appendFooter() { }

    protected abstract void appendData(List<? extends GridRow> rows);

    protected @NotNull GridColumn getRowNumbersColumn() {
      return new DataConsumer.Column(0, "#", Types.VARCHAR, "", "");
    }
  }
}
