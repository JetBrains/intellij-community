package com.intellij.database.csv;

import com.intellij.openapi.actionSystem.DataKey;

public interface CsvFormatEditor {
  DataKey<CsvFormatEditor> CSV_FORMAT_EDITOR_KEY = DataKey.create("CSV_FORMAT_EDITOR_KEY");

  boolean firstRowIsHeader();
  void setFirstRowIsHeader(boolean value);
}
