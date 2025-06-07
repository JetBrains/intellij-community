package com.intellij.database.csv;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CsvSettingsService {
  @NotNull CsvFormatsSettings getCsvSettings();

  /**
   * @deprecated Use CsvSettings
   */
  @Deprecated
  static @Nullable CsvFormatsSettings getDatabaseSettings() {
    CsvSettingsService service = ApplicationManager.getApplication().getService(CsvSettingsService.class);
    return service == null ? null : service.getCsvSettings();
  }
}
