package com.intellij.database.extractors;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

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

  /**
   * The {@link ExtractorConfigOption}s this factory supports — drives export config and UI checkbox visibility.
   * Returns a fresh, mutable copy of config options each call. Most factories support the TRANSPOSE option.
   * Overrides should compose via {@code DataExtractorFactory.super.getApplicableOptions()} and add/remove entries directly on that result.
   */
  default @NotNull Set<ExtractorConfigOption> getApplicableOptions() {
    return EnumSet.copyOf(EnumSet.of(ExtractorConfigOption.TRANSPOSE));
  }
}
