package com.intellij.database.extractors;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.util.Out;
import com.intellij.openapi.util.text.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author ignatov
 */
public class HtmlValuesExtractor extends TranspositionAwareExtractor {
  public HtmlValuesExtractor(ObjectFormatter converter) {
    super(converter);
  }

  public @NotNull String getDataPrefix() {
      return "<table border=\"1\" style=\"border-collapse:collapse\">";
  }

  public @NotNull String getDataSuffix() {
      return "</table>";
  }

  public @NotNull String getLinePrefix() {
    return "<tr>";
  }

  public @NotNull String getLineSuffix() {
    return "</tr>";
  }

  public @NotNull String getValuePrefix() {
    return "<td>";
  }

  public @NotNull String getValueSuffix() {
    return "</td>";
  }

  @Override
  public String getColumnName(GridColumn column) {
    return "<th>" + column.getName() + "</th>";
  }

  @Override
  protected @Nullable String getValueAsString(@NotNull GridRow row, @NotNull GridColumn column, @NotNull ObjectFormatterMode mode) {
    return getValueAsString(row, column, mode, true);
  }

  protected @Nullable String getValueAsString(@NotNull GridRow row,
                                              @NotNull GridColumn column,
                                              @NotNull ObjectFormatterMode mode,
                                              boolean escape) {
    String repr = super.getValueAsString(row, column, mode);
    return escape ? escapeChars(repr) : repr;
  }

  public static @Nullable String escapeChars(@Nullable String s) {
    return s != null ? StringUtil.escapeXmlEntities(s.replaceAll("[\t\b\\f]", "")).replaceAll("\\r|\\n|\\r\\n", "<br/>") : null;
  }

  @Override
  public @NotNull String getFileExtension() {
    return "html";
  }

  @Override
  public boolean supportsText() {
    return true;
  }

  @Override
  public TranspositionAwareExtraction startExtraction(@NotNull Out out,
                                                               @NotNull List<? extends GridColumn> allColumns,
                                                               @NotNull String query,
                                                               @NotNull ExtractionConfig config,
                                                               int... selectedColumns) {
    return new HtmlExtraction(out, config, allColumns, query, selectedColumns, false);
  }

  public Extraction spawnChildExtraction(@NotNull Out out,
                                         @NotNull List<? extends GridColumn> allColumns,
                                         @NotNull String query,
                                         @NotNull ExtractionConfig config,
                                         Boolean isOriginallyTransposed,
                                         int... selectedColumns) {
    return new HtmlExtraction(out, config, allColumns, query, selectedColumns, isOriginallyTransposed);
  }

  protected class HtmlExtraction extends TranspositionAwareExtraction {
    private final boolean myIsTransposedMode;
    public HtmlExtraction(Out out,
                          @NotNull ExtractionConfig config,
                          List<? extends GridColumn> allColumns,
                          String query,
                          int[] selectedColumnIndices,
                          Boolean isTransposedMode) {
      super(out, config, allColumns, query, selectedColumnIndices, HtmlValuesExtractor.this);
      myIsTransposedMode = isTransposedMode;
    }

    @Override
    protected Extraction spawnChildExtraction(@NotNull Out out,
                                              @NotNull List<? extends GridColumn> allColumns,
                                              @NotNull String query,
                                              @NotNull ExtractionConfig config,
                                              Boolean isOriginallyTransposed,
                                              int... selectedColumns) {
      return HtmlValuesExtractor.this
        .spawnChildExtraction(out, allColumns, query, config, isOriginallyTransposed, selectedColumns);
    }

    protected String getHeader() {
      return "<!DOCTYPE html>\n<html>\n<head>\n  <title></title>\n</head>\n<body>\n" + getDataPrefix() + "\n";
    }

    protected String getFooter() {
      return "\n</body>\n</html>";
    }

    @Override
    protected void doAppendData(List<? extends GridRow> rows) {
      Int2ObjectMap<? extends GridColumn> columnsMap = GridExtractorsUtilCore.getColumnNumsToColumnsMapping(myAllColumns);
      boolean first = true;
      for (GridRow row : rows) {
        if (!first) {
          myOut.appendText(getLineSeparator());
        }
        else {
          first = false;
        }

        myOut.appendText(getLinePrefix());
        if (myIsTransposedMode) {
          myOut.appendText(getValuePrefix()).appendText(getValueLiteral(getRowNumber(row), null, null)).appendText(getValueSuffix());
        }
        for (int selectedColumn : GridExtractorsUtilCore.getNonEmptySelection(myAllColumns, mySelectedColumnIndices)) {
          GridColumn column = columnsMap.get(selectedColumn);
          if (column == null) continue;

          String value = getValueLiteral(row, column, ObjectFormatterMode.DEFAULT);
          myOut.appendText(getValuePrefix()).appendText(value).appendText(getValueSuffix());
        }
        myOut.appendText(getLineSuffix());
      }
    }

    @Override
    protected void doAppendHeader(boolean appendNewLine) {
      myOut.appendText(getHeader());
      int[] selectedColumns = GridExtractorsUtilCore.getNonEmptySelection(myAllColumns, mySelectedColumnIndices);
      if (selectedColumns.length == 0) return;

      String columnName = myIsTransposedMode ? getColumnName(getRowNumbersColumn()) : "";
      myOut.appendText(getLinePrefix()).appendText(columnName);

      Int2ObjectMap<? extends GridColumn> columnsMap = GridExtractorsUtilCore.getColumnNumsToColumnsMapping(myAllColumns);
      for (int selectedColumn : selectedColumns) {
        GridColumn column = columnsMap.get(selectedColumn);
        if (column != null) myOut.appendText(getColumnName(column));
      }
      myOut.appendText(getLineSuffix());

      if (appendNewLine) myOut.appendText(getLineSeparator());
    }

    @Override
    protected void doAppendFooter() {
      myOut.appendText(getDataSuffix()).appendText(getFooter());
    }
  }
}
