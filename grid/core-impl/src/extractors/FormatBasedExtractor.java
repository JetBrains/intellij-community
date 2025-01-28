package com.intellij.database.extractors;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormatter;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.util.Out;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FormatBasedExtractor extends TranspositionAwareExtractor {
  private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile("[A-Z]{2,}");
  private final CsvFormat myFormat;

  public FormatBasedExtractor(@NotNull CsvFormat format, @NotNull ObjectFormatter formatter) {
    super(formatter);
    myFormat = format;
  }

  @Override
  public @NotNull String getFileExtension() {
    String ext = getFileExtension(myFormat);
    return ext != null ? ext : super.getFileExtension();
  }

  @Override
  public boolean supportsText() {
    return true;
  }

  public static @Nullable String getFileExtension(@NotNull CsvFormat format) {
    Matcher m = FILE_EXTENSION_PATTERN.matcher(format.name);
    String ext = m.find() ? m.group() : null;
    return StringUtil.isNotEmpty(ext) ? StringUtil.toLowerCase(ext) : null;
  }

  private static @NotNull CsvFormat adjust(@NotNull CsvFormat format, @Nullable Boolean addColumnHeader, @Nullable Boolean addRowHeader) {
    if (format.headerRecord == null && Boolean.TRUE.equals(addColumnHeader)) {
      format = new CsvFormat(format.name, format.dataRecord, format.dataRecord, format.rowNumbers);
    }
    else if (format.headerRecord != null && Boolean.FALSE.equals(addColumnHeader)) {
      format = new CsvFormat(format.name, format.dataRecord, null, format.rowNumbers);
    }
    if (addRowHeader != null && format.rowNumbers != addRowHeader) {
      format = new CsvFormat(format.name, format.dataRecord, format.headerRecord, addRowHeader);
    }
    return format;
  }

  @Override
  public TranspositionAwareExtraction startExtraction(
    @NotNull Out out,
    @NotNull List<? extends GridColumn> allColumns,
    @NotNull String query,
    @NotNull ExtractionConfig config,
    final int... selectedColumns
  ) {
    CsvFormat format = adjust(myFormat, config.getAddColumnHeader(), config.getAddRowHeader());
    return new TranspositionAwareExtraction(out, config, allColumns, query, selectedColumns, this) {
      private final CsvFormatter myFormatter = new CsvFormatter(format);
      @Override
      protected void doAppendData(List<? extends GridRow> rows) {
        List<GridColumn> columns = new ArrayList<>();
        Int2ObjectMap<? extends GridColumn> columnsMap = GridExtractorsUtilCore.getColumnNumsToColumnsMapping(myAllColumns);
        for (int selectedColumn : GridExtractorsUtilCore.getNonEmptySelection(myAllColumns, mySelectedColumnIndices)) {
          ContainerUtil.addIfNotNull(columns, columnsMap.get(selectedColumn));
        }

        for (final GridRow row : rows) {
          List<Object> values = ContainerUtil.map(columns, column -> getValueAsString(row, column, ObjectFormatterMode.DEFAULT));
          if (myFormatter.requiresRowNumbers()) {
            values = ContainerUtil.prepend(values, getRowNumber(row));
          }
          out.appendText(myFormatter.formatRecord(values));
          out.appendText(myFormatter.recordSeparator());
        }
      }

      @Override
      protected void doAppendHeader(boolean appendNewLine) {
        if (format.headerRecord == null) return;

        List<String> columnNames = new ArrayList<>();
        if (myFormatter.requiresRowNumbers()) {
          columnNames.add(getRowNumbersColumn().getName());
        }
        Int2ObjectMap<? extends GridColumn> columnsMap = GridExtractorsUtilCore.getColumnNumsToColumnsMapping(myAllColumns);
        for (int selectedColumn : GridExtractorsUtilCore.getNonEmptySelection(myAllColumns, mySelectedColumnIndices)) {
          GridColumn column = columnsMap.get(selectedColumn);
          if (column == null) continue;
          columnNames.add(column.getName());
        }

        out.appendText(myFormatter.formatHeader(columnNames));
        out.appendText(myFormatter.recordSeparator());
      }
    };
  }
}
