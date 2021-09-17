//package org.jetbrains.jps.cache.loader;
//
//import org.jetbrains.jps.cache.model.BuildTargetState;
//import org.jetbrains.jps.cache.model.JpsLoaderContext;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//import org.jetbrains.jps.cache.ui.SegmentedProgressIndicatorManager;
//
//import java.util.Map;
//
//interface JpsOutputLoader<T> {
//  T load(@NotNull JpsLoaderContext context);
//  LoaderStatus extract(@Nullable Object loadResults, @NotNull SegmentedProgressIndicatorManager extractIndicatorManager);
//  void rollback();
//  void apply(@NotNull SegmentedProgressIndicatorManager indicatorManager);
//  default int calculateDownloads(@NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
//                                 @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
//    return 1;
//  }
//
//  enum LoaderStatus {
//    COMPLETE, FAILED
//  }
//}