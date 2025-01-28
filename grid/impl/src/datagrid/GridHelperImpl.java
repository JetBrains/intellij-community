package com.intellij.database.datagrid;

import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.data.types.BaseDataTypeConversion;
import com.intellij.database.data.types.DataTypeConversion;
import com.intellij.database.dump.BaseGridHandler;
import com.intellij.database.dump.DumpHandler;
import com.intellij.database.dump.ExtractionHelper;
import com.intellij.database.extractors.DataExtractorFactory;
import com.intellij.database.extractors.ExtractionConfig;
import com.intellij.database.extractors.ObjectFormatterMode;
import com.intellij.database.run.actions.DumpSource;
import com.intellij.database.run.actions.DumpSource.DataGridSource;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.database.run.ui.DataAccessType.DATABASE_DATA;
import static com.intellij.util.containers.ContainerUtil.emptyList;

public class GridHelperImpl implements GridHelper {
  public static final String LIMIT_DEFAULT_PAGE_SIZE_PROP = "datagrid.limit.default.page.size";
  public static final String DEFAULT_PAGE_SIZE_PROP = "datagrid.default.page.size";
  public static final int DEFAULT_PAGE_SIZE = 10;
  public static final String LIMIT_DEFAULT_PAGE_SIZE_BIG_PROP = "datagrid.limit.default.page.size.big";
  public static final String DEFAULT_PAGE_SIZE_BIG_PROP = "datagrid.default.page.size.big";
  public static final int DEFAULT_PAGE_SIZE_BIG = 100;
  private final String myPageSizeKey;
  private final String myLimitPageSizeKey;
  private final int myDefaultPageSize;
  private final boolean myDefaultLimitPageSize;

  public GridHelperImpl() {
    this(true);
  }

  public GridHelperImpl(boolean insideNotebook) {
    this(insideNotebook, true);
  }

  public GridHelperImpl(boolean insideNotebook, boolean limitDefaultPageSizeBig) {
    this(insideNotebook ? DEFAULT_PAGE_SIZE_PROP : DEFAULT_PAGE_SIZE_BIG_PROP,
         insideNotebook ? LIMIT_DEFAULT_PAGE_SIZE_PROP : LIMIT_DEFAULT_PAGE_SIZE_BIG_PROP,
         insideNotebook ? DEFAULT_PAGE_SIZE : DEFAULT_PAGE_SIZE_BIG,
         insideNotebook || limitDefaultPageSizeBig);
  }

  public GridHelperImpl(@NotNull String pageSizeKey, @NotNull String limitPageSizeKey, int defaultPageSize, boolean defaultLimitPageSize) {
    myPageSizeKey = pageSizeKey;
    myLimitPageSizeKey = limitPageSizeKey;
    myDefaultPageSize = defaultPageSize;
    myDefaultLimitPageSize = defaultLimitPageSize;
  }

  @Override
  public @NotNull DataTypeConversion.Builder createDataTypeConversionBuilder() {
    return new BaseDataTypeConversion.Builder();
  }

  @Override
  public @NotNull ObjectFormatterMode getDefaultMode() {
    return ObjectFormatterMode.SQL_SCRIPT;
  }

  @Override
  public boolean canEditTogether(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull List<GridColumn> columns) {
    return true;
  }

  @Override
  public @Nullable GridColumn findUniqueColumn(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull List<GridColumn> columns) {
    return null;
  }

  @Override
  public @Nullable Icon getColumnIcon(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull GridColumn column, boolean forDisplay) {
    return null;
  }

  @Override
  public @Nullable VirtualFile getVirtualFile(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return GridUtil.getVirtualFile(grid);
  }

  @Override
  public void applyFix(@NotNull Project project, ErrorInfo.@NotNull Fix fix, @Nullable Object editor) {
  }

  @Override
  public @NotNull List<String> getUnambiguousColumnNames(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return emptyList();
  }

  @Override
  public boolean canAddRow(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return true;
  }

  @Override
  public @Nullable String getTableName(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return null;
  }

  @Override
  public @Nullable String getNameForDump(@NotNull DataGrid source) {
    return GridUtil.getEditorTabName(source);
  }

  @Override
  public @NotNull String getQueryText(@NotNull DataGrid source) {
    return "";
  }

  @Override
  public boolean isDatabaseHookUp(@NotNull DataGrid grid) {
    return false;
  }

  @Override
  public int getDefaultPageSize() {
    return PropertiesComponent.getInstance().getInt(myPageSizeKey, myDefaultPageSize);
  }

  @Override
  public void setDefaultPageSize(int value) {
    PropertiesComponent.getInstance().setValue(myPageSizeKey, value, myDefaultPageSize);
  }

  @Override
  public boolean isLimitDefaultPageSize() {
    return PropertiesComponent.getInstance().getBoolean(myLimitPageSizeKey, myDefaultLimitPageSize);
  }


  @Override
  public void setLimitDefaultPageSize(boolean value) {
    PropertiesComponent.getInstance().setValue(myLimitPageSizeKey, value, myDefaultLimitPageSize);
  }

  @Override
  public @Nullable DumpSource<?> createDumpSource(@NotNull DataGrid grid, @NotNull AnActionEvent e) {
    return new DataGridSource(grid);
  }

  @Override
  public @NotNull DumpHandler<?> createDumpHandler(@NotNull DumpSource<?> source,
                                                   @NotNull ExtractionHelper manager,
                                                   @NotNull DataExtractorFactory factory,
                                                   @NotNull ExtractionConfig config) {
    DataGridSource gridSource = (DataGridSource)source;
    DataGrid grid = gridSource.getGrid();
    return new BaseGridHandler(grid.getProject(), grid, gridSource.getNameProvider(), manager, factory, config) {
      @Override
      protected DataProducer createProducer(@NotNull DataGrid grid, int index) {
        GridModel<GridRow, GridColumn> model = grid.getDataModel(DATABASE_DATA);
        return new IdentityDataProducerImpl(new DataConsumer.Composite(),
                                            model.getColumns(),
                                            new ArrayList<>(model.getRows()),
                                            0,
                                            0);
      }
    };
  }

  @Override
  public boolean isMixedTypeColumns(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return true;
  }

  @Override
  public boolean isSortingApplicable() {
    return true;
  }

  @Override
  public boolean hasTargetForEditing(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return true;
  }

  @Override
  public boolean canMutateColumns(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return grid.isEditable() && grid.isReady() &&
           grid.getDataHookup() instanceof DocumentDataHookUp &&
           grid.getDataHookup().getMutator() instanceof GridMutator.ColumnsMutator<GridRow, GridColumn> &&
           grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getRowCount() != 0; // todo: check maybe it's okay to add columns to empty file
  }

  @Override
  public boolean isEditable(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return true;
  }

  @Override
  public void setFilterText(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull String text, int caretPosition) {
    grid.setFilterText(text, caretPosition);
  }

  @Override
  public @Nullable Language getCellLanguage(@NotNull CoreGrid<GridRow, GridColumn> grid,
                                            @NotNull ModelIndex<GridRow> row,
                                            @NotNull ModelIndex<GridColumn> column) {
    return null;
  }

  @Override
  public @Nullable PsiCodeFragment createCellCodeFragment(@NotNull String text,
                                                          @NotNull Project project,
                                                          @NotNull CoreGrid<GridRow, GridColumn> grid,
                                                          @NotNull ModelIndex<GridRow> row,
                                                          @NotNull ModelIndex<GridColumn> column) {
    return null;
  }
}
