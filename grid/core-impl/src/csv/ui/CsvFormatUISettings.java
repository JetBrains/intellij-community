package com.intellij.database.csv.ui;

public enum CsvFormatUISettings {
  DEFAULT(true),
  IMPORT_IN_TABLE(false);

  private final boolean myHeaderSettingsVisible;

  CsvFormatUISettings(boolean headerSettingsVisible) {
    myHeaderSettingsVisible = headerSettingsVisible;
  }

  public boolean isHeaderSettingsVisible() {
    return myHeaderSettingsVisible;
  }
}
