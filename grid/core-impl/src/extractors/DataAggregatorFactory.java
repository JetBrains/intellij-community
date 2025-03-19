package com.intellij.database.extractors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DataAggregatorFactory extends DataExtractorFactory {
  @Nullable
  DataExtractor createAggregator(@NotNull ExtractorConfig config);
}
