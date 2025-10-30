package com.intellij.database.extractors;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.util.Out;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.sql.Types;
import java.util.stream.IntStream;


public class PythonDataFrameExtractorFactory implements DataExtractorFactory {
  @Override
  public @NotNull String getName() {
    return DataGridBundle.message("python.pandas.dataframe");
  }

  @Override
  public boolean supportsText() {
    return true;
  }

  @Override
  public @NotNull String getFileExtension() {
    return "py";
  }

  @Override
  public @NotNull DataExtractor createExtractor(@NotNull ExtractorConfig config) {
    return new PythonDataFrameExtractor(config.getObjectFormatter());
  }

  private static class PythonDataFrameExtractor implements DataExtractor {
    private final ObjectFormatter myFormatter;
    private PythonDataFrameExtractor(@NotNull ObjectFormatter formatter) {
      myFormatter = formatter;
    }

    @Override
    public @NotNull String getFileExtension() {
      return "py";
    }

    @Override
    public boolean supportsText() {
      return true;
    }

    @Override
    public Extraction startExtraction(@NotNull Out out, @NotNull List<? extends GridColumn> allColumns, @NotNull String query, @NotNull ExtractionConfig config, int[] selectedColumns) {
      return new ExtractionImpl(out, allColumns, selectedColumns, myFormatter);
    }

    private static class ExtractionImpl implements DataExtractor.Extraction {
      private final Out myOut;
      private final ObjectFormatter myFormatter;
      private List<? extends GridColumn> myAllColumns;
      private final int[] mySelectedColumnIndices;
      private final List<GridRow> myRows = new ArrayList<>();

      ExtractionImpl(@NotNull Out out, @NotNull List<? extends GridColumn> allColumns, int @NotNull [] selectedColumns, @NotNull ObjectFormatter formatter) {
        myOut = out;
        myAllColumns = allColumns;
        mySelectedColumnIndices = selectedColumns;
        myFormatter = formatter;
      }

      @Override
      public void updateColumns(GridColumn @NotNull [] columns) {
        myAllColumns = Arrays.asList(columns);
      }

      @Override
      public void addData(List<? extends GridRow> rows) {
        myRows.addAll(rows);
      }

      @Override
      public void complete() {
        int[] selection = GridExtractorsUtilCore.getNonEmptySelection(myAllColumns, mySelectedColumnIndices);
        Int2ObjectMap<? extends GridColumn> columnMap = GridExtractorsUtilCore.getColumnNumsToColumnsMapping(myAllColumns);
        List<GridColumn> columns = IntStream.of(selection).skip(hasDummyName(selection, columnMap) ? 1 : 0).<GridColumn>mapToObj(columnMap::get).filter(Objects::nonNull).toList();
        StringBuilder sb = new StringBuilder("import pandas as pd\n\ndata = {");
        for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
          GridColumn column = columns.get(colIndex);
          sb.append("'").append(safePyString(column.getName())).append("': [");
          for (int rowIndex = 0; rowIndex < myRows.size(); rowIndex++) {
            sb.append(toPythonLiteral(column.getValue(myRows.get(rowIndex)), column));
            if (rowIndex < myRows.size() - 1) sb.append(", ");
          }
          sb.append(']');
          if (colIndex < columns.size() - 1) sb.append(", ");
        }
        sb.append("}\ndf = pd.DataFrame(data)\n");
        myOut.appendText(sb.toString());
      }

      private static boolean hasDummyName(int[] selection, Int2ObjectMap<? extends GridColumn> colMap) {
        if (selection.length == 0) return false;
        GridColumn firstColumn = colMap.get(selection[0]);
        if (firstColumn == null) return true;
        String name = firstColumn.getName();
        return name == null || name.isEmpty();
      }

      private @NotNull String toPythonLiteral(Object value, GridColumn column) {
        if (value == null) return "None";
        String s = myFormatter.objectToString(value, column, new DatabaseObjectFormatterConfig(ObjectFormatterMode.DEFAULT));
        if (s == null) return "None";
        int columnType = column.getType();
        if ((isNumericValueType(columnType) || columnType == Types.OTHER) && s.matches("-?\\d+(\\.\\d+)?") || columnType == Types.BOOLEAN) {
          return s;
        }
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
      }

      private static boolean isNumericValueType(int valueType) {
        return switch (valueType) {
          case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.FLOAT, Types.REAL,
               Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> true;
          default -> false;
        };
      }

      private static @NotNull String safePyString(@NotNull String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
      }
    }
  }
}
