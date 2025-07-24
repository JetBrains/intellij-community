package com.intellij.database.extractors;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.ExtractorHierarchicalGridColumn;
import com.intellij.database.util.Out;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class GridExtractorsUtilCore {
  public static int[] getNonEmptySelection(@NotNull List<? extends GridColumn> allColumns, int[] providedSelection) {
    if (providedSelection.length != 0) {
      // if columns are not hierarchical the providedSelection is valid
      if (ContainerUtil.and(allColumns, c -> !(c instanceof ExtractorHierarchicalGridColumn))) {
        return providedSelection;
      }

      // providedSelection consists of flat indexes of columns,
      // but in presense of hierarchical columns it should be modified.
      // In that case allColumns consist of corresponding roots of hierarchical columns
      // and the columns have method isMatchesSelection to check if its leaf-child were selected or not.
      Set<Integer> hierarchicalSelection = modifySelectionOfHierarchicalColumns(allColumns);

      return intSetToArray(hierarchicalSelection);
    }

    int[] selection = new int[allColumns.size()];
    for (int i = 0; i < allColumns.size(); i++) {
      selection[i] = allColumns.get(i).getColumnNumber();
    }
    return selection;
  }

  private static @NotNull Set<Integer> modifySelectionOfHierarchicalColumns(@NotNull List<? extends GridColumn> allColumns) {
    Set<Integer> resultIdxs = new LinkedHashSet<>();
    for (GridColumn column : allColumns) {
      ExtractorHierarchicalGridColumn hierarchical = (ExtractorHierarchicalGridColumn)column;

      if (!hierarchical.isMatchesSelection()) continue;
      resultIdxs.add(hierarchical.getColumnNumber());
    }
    return resultIdxs;
  }

  public static <T extends GridColumn> Int2ObjectMap<T> getColumnNumsToColumnsMapping(@NotNull List<T> columns) {
    Int2ObjectMap<T> m = new Int2ObjectOpenHashMap<>(columns.size());
    for (T column : columns) {
      m.put(column.getColumnNumber(), column);
    }
    return m;
  }

  public static Set<Integer> intArrayToSet(int[] array) {
    Set<Integer> set = new HashSet<>();
    for (int element : array) {
      set.add(element);
    }
    return set;
  }

  private static int[] intSetToArray(Set<Integer> set) {
    int[] array = new int[set.size()];
    int index = 0;

    for (Integer num : set) {
      array[index++] = num;
    }

    return array;
  }

  public static @NotNull Out extract(
    @NotNull Out out,
    @NotNull List<? extends GridColumn> allColumns,
    @NotNull DataExtractor extractor,
    @NotNull List<? extends GridRow> rows,
    int... selectedColumns
  ) {
    return extract(out, ExtractionConfigKt.DEFAULT_CONFIG, allColumns, extractor, rows, selectedColumns);
  }

  public static @NotNull Out extract(
    @NotNull Out out,
    @NotNull ExtractionConfig config,
    @NotNull List<? extends GridColumn> allColumns,
    @NotNull DataExtractor extractor,
    @NotNull List<? extends GridRow> rows,
    int... selectedColumns
  ) {
    DataExtractor.Extraction e = extractor.startExtraction(out, allColumns, "", config, selectedColumns);
    e.addData(rows);
    e.complete();
    return out;
  }

  public static @NotNull Out extractWithProgress(
    @NotNull Project project,
    @NotNull Out out,
    @NotNull ExtractionConfig config,
    @NotNull List<? extends GridColumn> allColumns,
    @NotNull DataExtractor extractor,
    @NotNull List<? extends GridRow> rows,
    int... selectedColumns
  ) {
    DataExtractor.Extraction e = extractor.startExtraction(out, allColumns, "", config, selectedColumns);
    boolean ok = ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(new ExtractionRunnable(e, rows),
                                           DataGridBundle.message("dialog.title.extraction.with.progress"),
                                           true, project);
    if (ok) e.complete();

    return out;
  }

  private static class ExtractionRunnable implements Runnable {
    private final DataExtractor.Extraction extraction;

    private final List<? extends GridRow> rows;

    private ExtractionRunnable(@NotNull DataExtractor.Extraction extraction, @NotNull List<? extends GridRow> rows) {
      this.extraction = extraction;
      this.rows = rows;
    }

    @Override
    public void run() {
      extraction.addData(rows);
    }
  }

  public static DataExtractor getSingleValueExtractor(@NotNull ObjectFormatter converter, Function<? super Integer, ObjectFormatterConfig> configProvider) {
    return new DefaultValuesExtractor(converter) {
      @Override
      protected boolean isStringLiteral(@Nullable GridRow row, @Nullable GridColumn column) {
        return false;
      }

      @Override
      public boolean supportsText() {
        return true;
      }

      @Override
      public DefaultExtraction startExtraction(@NotNull Out out,
                                               @NotNull List<? extends GridColumn> allColumns,
                                               @NotNull String query,
                                               @NotNull ExtractionConfig config, int... selectedColumns) {
        return new DefaultExtraction(out, ExtractionConfigKt.DEFAULT_CONFIG, allColumns, query, selectedColumns) {
          @Override
          protected void appendData(List<? extends GridRow> rows) {
            Int2ObjectMap<? extends GridColumn> columnsMap = getColumnNumsToColumnsMapping(myAllColumns);
            int[] selection = getNonEmptySelection(myAllColumns, mySelectedColumnIndices);
            if (rows.isEmpty() || selection.length == 0) return;
            int selectedColumn = selection[0];
            GridColumn column = columnsMap.get(selectedColumn);
            if (column == null) return;
            GridRow row = rows.get(0);
            ObjectFormatterConfig config = configProvider.apply(column.getColumnNumber());
            out.appendText(getValueLiteral(row, column, config));
          }
        };
      }
    };
  }

  public static String prepareFileName(@NotNull String name) {
    return StringUtil.trimEnd(FileUtil.sanitizeFileName(name), "_");
  }
}