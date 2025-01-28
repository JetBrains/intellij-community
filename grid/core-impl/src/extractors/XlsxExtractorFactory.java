package com.intellij.database.extractors;

import com.intellij.database.DataGridBundle;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;

public class XlsxExtractorFactory implements DataExtractorFactory {
  @Override
  public @NotNull String getName() {
    return DataGridBundle.message("excel.xlsx");
  }

  @Override
  public boolean supportsText() {
    return false;
  }

  @Override
  public @NotNull String getFileExtension() {
    return "xlsx";
  }

  @Override
  public @NotNull XlsxValuesExtractor createExtractor(@NotNull ExtractorConfig config) {
    return new XlsxValuesExtractor(config.getObjectFormatter());
  }
}
