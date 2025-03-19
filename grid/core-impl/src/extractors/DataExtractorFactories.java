package com.intellij.database.extractors;

import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormats;
import com.intellij.database.csv.CsvFormatsSettings;
import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.extensions.ExtractorScripts;
import com.intellij.database.settings.CsvSettings;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PairFunction;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

public final class DataExtractorFactories {
  public static final String JSON_EXTRACTOR_FACTORY_ID = "JSON-Groovy.json.groovy";
  public static final Key<DataExtractorFactory> GRID_DATA_EXTRACTOR_FACTORY_KEY = new Key<>("GRID_DATA_EXTRACTOR_FACTORY_KEY");
  public static final Key<Boolean> GRID_DATA_SKIP_COMPUTED_COLUMNS_KEY = new Key<>("GRID_DATA_SKIP_COMPUTED_COLUMNS_KEY");
  public static final Key<Boolean> GRID_DATA_SKIP_GENERATED_COLUMNS_KEY = new Key<>("GRID_DATA_SKIP_GENERATED_COLUMNS_KEY");

  private DataExtractorFactories() {
  }

  public static @NotNull List<? extends DataExtractorFactory> getBuiltInFactories(@Nullable CoreGrid<GridRow, GridColumn> grid) {
    return ExtractorsHelper.getInstance(grid).getBuiltInFactories();
  }

  public static @NotNull List<DataExtractorFactory> getBuiltInFactories() {
    List<DataExtractorFactory> factories = new ArrayList<>();
    for (ExtractorsHelper helper : ExtractorsHelper.EP.getExtensionList()) {
      factories.addAll(helper.getBuiltInFactories());
    }
    return factories;
  }

  public static @NotNull List<DataExtractorFactory> getCsvFormats(@Nullable CsvFormatsSettings settings) {
    List<CsvFormat> formats = settings == null ? Collections.emptyList() : settings.getCsvFormats();
    return ContainerUtil.map(formats, FormatExtractorFactory::new);
  }

  public static @NlsActions.ActionDescription @NotNull String getDisplayName(@NotNull DataExtractorFactory factory, @NotNull List<? extends DataExtractorFactory> scripts) {
    if (!(factory instanceof BaseExtractorsHelper.Script)) {
      return factory.getName();
    }
    String simpleName = factory.getSimpleName();
    boolean isDuplicateName = ContainerUtil.count(scripts, f -> f.getSimpleName().equals(simpleName)) > 1;
    @NlsSafe
    String scriptExtension = FileUtilRt.getExtension(factory.getName());
    return isDuplicateName && !scriptExtension.isEmpty() ? simpleName + " (" + scriptExtension + ")" : simpleName;
  }

  public static @NotNull List<DataExtractorFactory> getExtractorScripts(@NotNull ExtractorsHelper provider, @Nullable BiConsumer<String, Project> installPlugin) {
    return JBIterable.from(ExtractorScripts.getExtractorScriptFiles())
      .map(o -> provider.createScriptExtractorFactory(o.getFileName().toString(), installPlugin))
      .toList();
  }

  public static @NotNull List<DataAggregatorFactory> getAggregatorScripts(@NotNull ExtractorsHelper provider, @Nullable BiConsumer<String, Project> installPlugin) {
    try (AccessToken ignore = SlowOperations.knownIssue("DBE-19294, EA-662240")) {
      return JBIterable.from(ExtractorScripts.getAggregatorScriptFiles())
        .map(o -> provider.createScriptAggregatorFactory(o.getFileName().toString(), installPlugin))
        .toList();
    }
  }

  public static @NotNull DataExtractorFactory getDefault(@Nullable CsvFormatsSettings settings) {
    List<DataExtractorFactory> formats = getCsvFormats(settings);
    String csvId = CsvFormats.CSV_FORMAT.getValue().id;
    DataExtractorFactory csvFactory = findById(csvId, formats);
    return csvFactory != null ? csvFactory :
           formats.isEmpty() ? new FormatExtractorFactory(CsvFormats.CSV_FORMAT.getValue()) :
           Objects.requireNonNull(ContainerUtil.getFirstItem(formats));
  }


  public static @Nullable DataExtractorFactory findById(@NotNull String id,
                                                        @Nullable BiConsumer<String, Project> installPlugin,
                                                        @Nullable CsvFormatsSettings settings) {
    return find(id, installPlugin, DataExtractorFactories::findById, settings);
  }

  public static @NotNull DataExtractorFactory create(@NotNull CsvFormat format) {
    return new FormatExtractorFactory(format);
  }

  private static @Nullable DataExtractorFactory findById(@NotNull String id, @NotNull List<? extends DataExtractorFactory> factories) {
    return ContainerUtil.find(factories, (Condition<DataExtractorFactory>)factory -> id.equals(factory.getId()));
  }

  private static @Nullable DataExtractorFactory find(@NotNull String key,
                                                     @Nullable BiConsumer<String, Project> installPlugin,
                                                     @NotNull PairFunction<String, List<? extends DataExtractorFactory>, DataExtractorFactory> finder,
                                                     @Nullable CsvFormatsSettings settings) {
    DataExtractorFactory f = finder.fun(key, getBuiltInFactories());
    f = f != null ? f : finder.fun(key, getCsvFormats(settings));
    f = f != null ? f : finder.fun(key, getExtractorScripts(ExtractorsHelper.getInstance(null), installPlugin));
    return f;
  }

  public static @NotNull DataExtractorFactory getExtractorFactory(@NotNull CoreGrid<GridRow, GridColumn> grid, @Nullable BiConsumer<String, Project> installPlugin) {
    DataExtractorFactory factory = grid.getUserData(GRID_DATA_EXTRACTOR_FACTORY_KEY);
    if (factory == null) {
      CsvFormatsSettings settings = CsvSettings.getSettings();
      factory = ExtractorsHelper.getInstance(grid).getDefaultExtractorFactory(grid, installPlugin, settings);
      if (factory == null) {
        factory = DataExtractorProperties.getCurrentExtractorFactory(grid.getProject(), installPlugin, settings);
      }
      grid.putUserData(GRID_DATA_EXTRACTOR_FACTORY_KEY, factory);

      /* update extractor factory in Data View after CSV format is modified */
      MessageBusConnection connection = grid.getProject().getMessageBus().connect(grid);
      connection.subscribe(CsvFormatsSettings.TOPIC, () -> {
        DataExtractorFactory f = grid.getUserData(GRID_DATA_EXTRACTOR_FACTORY_KEY);
        if (f == null) return;
        DataExtractorFactory updated = findById(f.getId(), installPlugin, settings);
        if (updated != null) setExtractorFactory(grid, updated);
      });
    }
    return factory;
  }

  public static void setExtractorFactory(@NotNull CoreGrid<?, ?> grid, @NotNull DataExtractorFactory factory) {
    grid.putUserData(GRID_DATA_EXTRACTOR_FACTORY_KEY, factory);
    grid.getResultView().extractorFactoryChanged();
  }

  public static boolean getSkipComputedColumns(@NotNull CoreGrid<?, ?> grid) {
    Boolean value = grid.getUserData(GRID_DATA_SKIP_COMPUTED_COLUMNS_KEY);
    if (value == null) {
      value = DataExtractorProperties.isSkipComputed();
      grid.putUserData(GRID_DATA_SKIP_COMPUTED_COLUMNS_KEY, value);
    }
    return value;
  }

  public static boolean getSkipGeneratedColumns(@NotNull CoreGrid<?, ?> grid) {
    Boolean value = grid.getUserData(GRID_DATA_SKIP_GENERATED_COLUMNS_KEY);
    if (value == null) {
      value = DataExtractorProperties.isSkipGeneratedColumns();
      grid.putUserData(GRID_DATA_SKIP_GENERATED_COLUMNS_KEY, value);
    }
    return value;
  }

  public static void setSkipComputedColumns(@NotNull CoreGrid<?, ?> grid, boolean value) {
    grid.putUserData(GRID_DATA_SKIP_COMPUTED_COLUMNS_KEY, value);
    grid.getResultView().extractorFactoryChanged();
  }

  public static void setSkipGeneratedColumns(@NotNull CoreGrid<?, ?> grid, boolean value) {
    grid.putUserData(GRID_DATA_SKIP_GENERATED_COLUMNS_KEY, value);
    grid.getResultView().extractorFactoryChanged();
  }
}
