package com.intellij.database.extractors;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DataExtractorFactory {

  @Nls
  @NotNull
  String getName();

  boolean supportsText();

  default @Nls @NotNull String getSimpleName() {
    return getName();
  }

  @Nullable
  DataExtractor createExtractor(@NotNull ExtractorConfig config);



  default @NonNls @NotNull String getId() {
    return getName();
  }

  @NotNull
  String getFileExtension();
}
