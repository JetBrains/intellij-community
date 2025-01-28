package com.intellij.database.datagrid;

import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.data.types.DataTypeConversion;
import com.intellij.database.dump.DumpHandler;
import com.intellij.database.dump.ExtractionHelper;
import com.intellij.database.extractors.*;
import com.intellij.database.run.actions.DumpSource;
import com.intellij.database.run.ui.grid.DefaultGridColumnLayout;
import com.intellij.database.run.ui.grid.GridRowComparator;
import com.intellij.database.run.ui.table.TableResultView;
import com.intellij.database.util.Out;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

import static com.intellij.database.datagrid.GridUtil.getSelectedGridRows;
import static com.intellij.database.datagrid.GridUtilKt.findAllGridsInFile;
import static com.intellij.database.extractors.ExtractionConfigKt.builder;
import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;

public interface GridHelper extends CoreGridHelper {
  Key<GridHelper> GRID_HELPER_KEY = new Key<>("GRID_HELPER_KEY");

  @NotNull
  DataTypeConversion.Builder createDataTypeConversionBuilder();

  @NotNull ObjectFormatterMode getDefaultMode();

  boolean canEditTogether(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull List<GridColumn> columns);

  default boolean canSortTogether(@NotNull CoreGrid<GridRow, GridColumn> grid,
                                  @NotNull List<ModelIndex<GridColumn>> oldOrdering,
                                  List<ModelIndex<GridColumn>> newColumns) {
    return true;
  }

  @Nullable
  GridColumn findUniqueColumn(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull List<GridColumn> columns);

  @Nullable
  Icon getColumnIcon(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull GridColumn column, boolean forDisplay);

  @Nullable VirtualFile getVirtualFile(@NotNull CoreGrid<GridRow, GridColumn> grid);

  default @NotNull JBIterable<TreeElement> getChildrenFromModel(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return JBIterable.empty();
  }

  default @Nullable String getLocationString(@Nullable PsiElement element) {
    return null;
  }

  default void setFilterSortHighlighter(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull Editor editor) {
  }

  default void updateFilterSortPSI(@NotNull CoreGrid<GridRow, GridColumn> grid) {
  }

  void applyFix(@NotNull Project project, @NotNull ErrorInfo.Fix fix, @Nullable Object editor);

  @NotNull
  List<String> getUnambiguousColumnNames(@NotNull CoreGrid<GridRow, GridColumn> grid);

  boolean canAddRow(@NotNull CoreGrid<GridRow, GridColumn> grid);

  boolean hasTargetForEditing(@NotNull CoreGrid<GridRow, GridColumn> grid); // DBE-12001

  @Nullable
  String getTableName(@NotNull CoreGrid<GridRow, GridColumn> grid);

  @Nullable
  String getNameForDump(@NotNull DataGrid source);

  @Nullable
  String getQueryText(@NotNull DataGrid source);

  boolean isDatabaseHookUp(@NotNull DataGrid grid);

  int getDefaultPageSize();

  void setDefaultPageSize(int value);

  boolean isLimitDefaultPageSize();

  void setLimitDefaultPageSize(boolean value);

  @Nullable
  DumpSource<?> createDumpSource(@NotNull DataGrid grid, @NotNull AnActionEvent e);

  @NotNull
  DumpHandler<?> createDumpHandler(@NotNull DumpSource<?> source,
                                   @NotNull ExtractionHelper manager,
                                   @NotNull DataExtractorFactory factory,
                                   @NotNull ExtractionConfig config);

  default boolean isDumpEnabled(@NotNull DumpSource<?> source) {
    return true;
  }

  default void syncExtractorsInNotebook(@NotNull DataGrid grid, @NotNull DataExtractorFactory factory) {
    findAllGridsInFile(grid).forEach((g) -> {
      DataExtractorFactory f = g.getUserData(DataExtractorFactories.GRID_DATA_EXTRACTOR_FACTORY_KEY);
      if (f == null || !factory.getId().equals(f.getId())) {
        DataExtractorFactories.setExtractorFactory(g, factory);
      }
    });
  }

  default boolean isLoadWholeTableWhenPaginationIsOff(@NotNull DataGrid grid) {
    return false;
  }

  static @NotNull GridHelper get(@NotNull CoreGrid<?, ?> grid) {
    return Objects.requireNonNull(GRID_HELPER_KEY.get(grid));
  }

  static boolean supportsTableStatistics(DataGrid grid) {
    if (grid == null) return false;

    if (grid.getResultView() instanceof TableResultView tableResultView) {
      return tableResultView.getStatisticsHeader() != null;
    } else {
      return false;
    }
  }

  default @NotNull GridColumnLayout<GridRow, GridColumn> createColumnLayout(@NotNull TableResultView resultView, @NotNull DataGrid grid) {
    return new DefaultGridColumnLayout(resultView, grid);
  }

  static void set(@NotNull CoreGrid<?, ?> grid, @NotNull GridHelper helper) {
    GRID_HELPER_KEY.set(grid, helper);
  }

  default @NlsContexts.Tooltip @Nullable String getColumnTooltipHtml(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndex<GridColumn> columnIdx) {
    return null;
  }

  default @NlsSafe @Nullable String getDatabaseSystemName(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return null;
  }

  boolean isEditable(@NotNull CoreGrid<GridRow, GridColumn> grid);

  default @Nullable GridRowComparator createComparator(@NotNull GridColumn column) {
    return GridRowComparator.create(column);
  }

  default @NotNull Out extractValues(@NotNull DataGrid dataGrid,
                                     @NotNull DataExtractor extractor,
                                     @NotNull Out out,
                                     boolean selection,
                                     boolean transpositionAllowed) {
    GridModel<GridRow, GridColumn> model = dataGrid.getDataModel(DATA_WITH_MUTATIONS);
    int[] columns = (selection ? dataGrid.getSelectionModel().getSelectedColumns() : model.getColumnIndices()).asArray();
    List<GridRow> rows = selection ? getSelectedGridRows(dataGrid) : model.getRows();
    boolean transposed = transpositionAllowed && dataGrid.getResultView().isTransposed();
    ExtractionConfig config = builder()
      .setTransposed(transposed)
      .setAddGeneratedColumns(!DataExtractorFactories.getSkipGeneratedColumns(dataGrid))
      .setAddComputedColumns(!DataExtractorFactories.getSkipComputedColumns(dataGrid))
      .build();
    GridExtractorsUtilCore.extract(out, config,
                                   model.getAllColumnsForExtraction(columns),
                                   extractor, rows, columns);

    return out;
  }

  @SuppressWarnings("UnusedReturnValue")
  default @NotNull Out extractValuesForCopy(@NotNull DataGrid dataGrid,
                                     @NotNull DataExtractor extractor,
                                     @NotNull Out out,
                                     boolean selection,
                                     boolean transpositionAllowed) {
    return extractValues(dataGrid, extractor, out, selection, transpositionAllowed);
  }

  default boolean isColumnContainNestedTables(@Nullable GridModel<GridRow, GridColumn> gridModel, @NotNull GridColumn column) {
    return false;
  }
}
