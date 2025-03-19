package com.intellij.database;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;

/**
 * @author Gregory.Shrago
 */
public final class DatabaseDataKeys {
  public static final Key<Boolean> DETECT_TEXT_IN_BINARY_COLUMNS = new Key<>("DETECT_TEXT_IN_BINARY_COLUMNS");
  public static final Key<Boolean> DETECT_UUID_IN_BINARY_COLUMNS = new Key<>("DETECT_TEXT_IN_BINARY_COLUMNS");
  public static final Key<DataGridSettings> DATA_GRID_SETTINGS_KEY = new Key<>("DATA_GRID_SETTINGS_KEY");
  public static final DataKey<RunnerLayoutUi> DATA_GRID_RUNNER_LAYOUT_UI_KEY = DataKey.create("DATA_GRID_RUNNER_LAYOUT_UI_KEY");
  public static final DataKey<Content> DATA_GRID_CONTENT_KEY = DataKey.create("DATA_GRID_CONTENT_KEY");

  private DatabaseDataKeys() {
  }

  public static final DataKey<DataGrid> DATA_GRID_KEY = DataKey.create("DATA_GRID_KEY");
  public static final Key<DataGrid> GRID_KEY = Key.create("GRID_KEY");
}
