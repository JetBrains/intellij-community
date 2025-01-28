package com.intellij.database.extractors;

import com.intellij.database.csv.CsvFormat;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FormatExtractorFactory implements DataExtractorFactory {
  private final CsvFormat myFormat;

  FormatExtractorFactory(@NotNull CsvFormat format) {
    myFormat = format;
  }

  @Override
  public @NotNull String getName() {
    return myFormat.name;
  }

  @Override
  public boolean supportsText() {
    return true;
  }

  @Override
  public @Nullable DataExtractor createExtractor(@NotNull ExtractorConfig config) {
    return new FormatBasedExtractor(myFormat, config.getObjectFormatter());
  }

  @Override
  public @NotNull String getFileExtension() {
    return ObjectUtils.notNull(FormatBasedExtractor.getFileExtension(myFormat), "txt");
  }

  @Override
  public @NotNull String getId() {
    return myFormat.id;
  }

  public @NotNull CsvFormat getFormat() {
    return myFormat;
  }
}
