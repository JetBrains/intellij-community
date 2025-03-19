package com.intellij.database.extractors;

import com.intellij.database.csv.CsvFormatsSettings;
import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.extensions.ExtensionScriptsUtil;
import com.intellij.database.extensions.ExtractorScripts;
import com.intellij.ide.script.IdeScriptEngine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

public class BaseExtractorsHelper implements ExtractorsHelper {
  public static final BaseExtractorsHelper INSTANCE = new BaseExtractorsHelper();

  protected BaseExtractorsHelper() {
  }

  @Override
  public boolean isApplicable(@Nullable CoreGrid<GridRow, GridColumn> grid) {
    return true;
  }

  @Override
  public @NotNull DataExtractorFactory createScriptExtractorFactory(@NotNull String scriptFileName,
                                                                    @Nullable BiConsumer<String, Project> installPlugin) {
    return new Script(scriptFileName, installPlugin);
  }

  @Override
  public @NotNull DataAggregatorFactory createScriptAggregatorFactory(@NotNull String scriptFileName,
                                                                      @Nullable BiConsumer<String, Project> installPlugin) {
    return new Script(scriptFileName, installPlugin);
  }

  @Override
  public DataExtractorFactory getDefaultExtractorFactory(@NotNull CoreGrid<GridRow, GridColumn> grid,
                                                              @Nullable BiConsumer<String, Project> installPlugin,
                                                              @Nullable CsvFormatsSettings settings) {
    return null;
  }

  @Override
  public @NotNull ExtractorConfig createExtractorConfig(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ObjectFormatter formatter) {
    return new BaseExtractorConfig(formatter, grid.getProject());
  }

  @Override
  public @NotNull List<DataExtractorFactory> getBuiltInFactories() {
    return List.of(new XlsxExtractorFactory());
  }

  public static class Script implements DataExtractorFactory, DataAggregatorFactory {
    protected final String myScriptFileName;
    protected final BiConsumer<String, Project> myInstallPlugin;
    private final String mySimpleName;

    public Script(@NotNull String scriptFileName, @Nullable BiConsumer<String, Project> installPlugin) {
      myScriptFileName = scriptFileName;
      myInstallPlugin = installPlugin;
      mySimpleName = extractSimpleName(myScriptFileName);
    }

    public static @NlsSafe @NotNull String extractSimpleName(String fileName) {
      final String mySimpleName;
      int dot = fileName.indexOf('.');
      String simpleName = dot == -1 ? fileName : fileName.substring(0, dot);
      int dash = simpleName.lastIndexOf('-');
      String lastPart = dash == -1 || dash == simpleName.length() - 1 ? "" : simpleName.substring(dash + 1);
      mySimpleName = StringUtil.equalsIgnoreCase(lastPart, "groovy") || StringUtil.equalsIgnoreCase(lastPart, "javascript")
                     ? simpleName.substring(0, dash)
                     : simpleName;
      return mySimpleName;
    }

    @Override
    public @NlsSafe @NotNull String getName() {
      return myScriptFileName;
    }

    @Override
    public boolean supportsText() {
      return true; // TODO: it depends, actually
    }

    public @NotNull String getScriptFileName() {
      return myScriptFileName;
    }

    @Override
    public @NlsSafe @NotNull String getSimpleName() {
      return mySimpleName;
    }

    @Override
    public @NotNull String getFileExtension() {
      return ExtractorScripts.getOutputFileExtension(myScriptFileName);
    }

    @Override
    public @Nullable DataExtractor createExtractor(@NotNull ExtractorConfig config) {
      Path script = ExtractorScripts.findExtractorScript(myScriptFileName);
      if (script == null) {
        return null;
      }

      ExtensionScriptsUtil.prepareScript(script);
      IdeScriptEngine engine = ExtensionScriptsUtil.getEngineFor(config.getProject(), ExtractorScripts.getPluginId(), script, myInstallPlugin);
      return engine == null ? null : new NoDbScriptDataExtractor(config.getProject(), script, engine, config.getObjectFormatter(), false, true /* TODO: support detection */);
    }

    @Override
    public @Nullable DataExtractor createAggregator(@NotNull ExtractorConfig config) {
      Path script = ExtractorScripts.findAggregatorScript(myScriptFileName);
      if (script == null) {
        return null;
      }

      ExtensionScriptsUtil.prepareScript(script);

      IdeScriptEngine engine = ExtensionScriptsUtil.getEngineFor(config.getProject(), ExtractorScripts.getPluginId(), script, null, false);
      return engine == null ? null : new NoDbScriptDataExtractor(config.getProject(), script, engine, config.getObjectFormatter(), true, true /* TODO: support detection */);
    }
  }
}
