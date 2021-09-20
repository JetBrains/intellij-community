package org.jetbrains.jps.cache.loader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cache.model.BuildTargetState;
import org.jetbrains.jps.cache.model.JpsLoaderContext;

import java.util.Map;

interface JpsOutputLoader<T> {
  T load();
  LoaderStatus extract(@Nullable Object loadResults);
  void rollback();
  void apply();
  void setContext(@NotNull JpsLoaderContext context);
  default int calculateDownloads(@NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
                                 @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
    return 1;
  }

  enum LoaderStatus {
    COMPLETE, FAILED
  }
}