package com.intellij.database.extractors;

import com.intellij.database.csv.CsvFormatsSettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

public final class DataExtractorProperties {

  private static final String CURRENT_ID = "database.data.extractors.current.id";
  private static final String CURRENT_EXPORT_ID = "database.data.extractors.current.export.id";

  private static final String SKIP_GENERATED = "database.data.extractors.sql.skip.generated";
  private static final String SKIP_COMPUTED = "database.data.extractors.sql.skip.computed";
  private static final String CREATE_TABLE = "database.data.extractors.sql.create.table";
  private static final String INCLUDE_QUERY = "database.data.extractors.include.query";
  private static final String OUTPUT_DIR = "database.data.extractors.output.dir";

  private static final List<String> FALSE_AS_DEFAULT_PROPS = Arrays.asList(CREATE_TABLE, SKIP_GENERATED);

  private DataExtractorProperties() {
  }

  public static @NotNull DataExtractorFactory getCurrentExtractorFactory(@Nullable Project project,
                                                                         @Nullable BiConsumer<String, Project> installPlugin,
                                                                         @Nullable CsvFormatsSettings settings) {
    PropertiesComponent storage = project == null ? null : PropertiesComponent.getInstance(project);
    String factoryId = storage != null ? storage.getValue(CURRENT_ID) : null;
    DataExtractorFactory factory = factoryId == null ? null : DataExtractorFactories.findById(factoryId, installPlugin, settings);
    return factory != null ? factory : DataExtractorFactories.getDefault(settings);
  }

  public static @NotNull DataExtractorFactory getCurrentExportExtractorFactory(@Nullable Project project,
                                                                               @Nullable BiConsumer<String, Project> installPlugin,
                                                                               @Nullable CsvFormatsSettings settings) {
    PropertiesComponent storage = project == null ? null : PropertiesComponent.getInstance(project);
    String factoryId = storage != null ? storage.getValue(CURRENT_EXPORT_ID) : null;
    DataExtractorFactory factory = factoryId == null ? null : DataExtractorFactories.findById(factoryId, installPlugin, settings);
    return factory != null ? factory : getCurrentExtractorFactory(project, installPlugin, settings);
  }

  public static void setCurrentExtractorFactory(@NotNull Project project, @NotNull DataExtractorFactory f) {
    PropertiesComponent component = PropertiesComponent.getInstance(project);
    component.setValue(CURRENT_ID, f.getId());
  }

  public static void setCurrentExportExtractorFactory(@NotNull Project project, @NotNull DataExtractorFactory f) {
    PropertiesComponent component = PropertiesComponent.getInstance(project);
    component.setValue(CURRENT_EXPORT_ID, f.getId());
  }

  public static boolean isSkipGeneratedColumns() {
    return getAppProperty(SKIP_GENERATED);
  }

  public static void setSkipGeneratedColumns(boolean value) {
    setAppProperty(SKIP_GENERATED, value);
  }

  public static boolean isSkipComputed() {
    return getAppProperty(SKIP_COMPUTED);
  }

  public static void setSkipComputed(boolean value) {
    setAppProperty(SKIP_COMPUTED, value);
  }

  public static boolean isIncludeCreateTable() {
    return getAppProperty(CREATE_TABLE);
  }

  public static void setIncludeCreateTable(boolean value) {
    setAppProperty(CREATE_TABLE, value);
  }

  public static boolean isIncludeQuery() {
    return getAppProperty(INCLUDE_QUERY);
  }

  public static void setIncludeQuery(boolean value) {
    setAppProperty(INCLUDE_QUERY, value);
  }

  public static void setOutputDir(String value) {
    if (getDefaultOutputDir().equals(value) || StringUtil.isEmptyOrSpaces(value)) {
      setAppProperty(OUTPUT_DIR, null);
    }
    else {
      setAppProperty(OUTPUT_DIR, value);
    }
  }

  public static @NotNull String getOutputDir() {
    String value = PropertiesComponent.getInstance().getValue(OUTPUT_DIR);
    return value != null && !StringUtil.isEmptyOrSpaces(value) ? value : getDefaultOutputDir();
  }

  private static @NotNull String getDefaultOutputDir() {
    return SystemProperties.getUserHome();
  }

  private static boolean getAppProperty(@NotNull String property) {
    boolean defaultValue = !FALSE_AS_DEFAULT_PROPS.contains(property);
    return PropertiesComponent.getInstance().getBoolean(property, defaultValue);
  }

  private static void setAppProperty(@NotNull String property, boolean value) {
    boolean defaultValue = !FALSE_AS_DEFAULT_PROPS.contains(property);
    PropertiesComponent.getInstance().setValue(property, value, defaultValue);
  }

  private static void setAppProperty(@NotNull String property, @Nullable String value) {
    PropertiesComponent.getInstance().setValue(property, value);
  }

  private static @NotNull String getStringAppProperty(@NotNull String property) {
    return PropertiesComponent.getInstance().getValue(property, "");
  }
}
