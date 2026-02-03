package com.intellij.database.extractors;

import com.intellij.database.csv.CsvFormatsSettings;
import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

public interface ExtractorsHelper {
  ExtensionPointName<ExtractorsHelper> EP = ExtensionPointName.create("com.intellij.database.datagrid.extractorsHelper");

  boolean isApplicable(@Nullable CoreGrid<GridRow, GridColumn> grid);

  @NotNull
  DataExtractorFactory createScriptExtractorFactory(@NotNull String scriptFileName,
                                                             @Nullable BiConsumer<String, Project> installPlugin);

  @NotNull
  DataAggregatorFactory createScriptAggregatorFactory(@NotNull String scriptFileName, @Nullable BiConsumer<String, Project> installPlugin);

  @NotNull List<DataExtractorFactory> getBuiltInFactories();

  DataExtractorFactory getDefaultExtractorFactory(@NotNull CoreGrid<GridRow, GridColumn> grid,
                                                       @Nullable BiConsumer<String, Project> installPlugin,
                                                       @Nullable CsvFormatsSettings settings);

  @NotNull
  ExtractorConfig createExtractorConfig(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ObjectFormatter formatter);

  static @NotNull ExtractorsHelper getInstance(@Nullable CoreGrid<GridRow, GridColumn> grid) {
    ExtractorsHelper helper = ContainerUtil.find(EP.getExtensionList(), p -> p.isApplicable(grid));
    return helper != null ? helper : BaseExtractorsHelper.INSTANCE;
  }
}
