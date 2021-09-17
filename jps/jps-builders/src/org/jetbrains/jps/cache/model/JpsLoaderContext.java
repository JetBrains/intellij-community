//package org.jetbrains.jps.cache.model;
//
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//import org.jetbrains.jps.cache.ui.SegmentedProgressIndicatorManager;
//
//import java.util.Map;
//
//public final class JpsLoaderContext {
//  private final String commitId;
//  private final SegmentedProgressIndicatorManager downloadIndicatorManager;
//  private final Map<String, Map<String, BuildTargetState>> commitSourcesState;
//  private final Map<String, Map<String, BuildTargetState>> currentSourcesState;
//
//  private JpsLoaderContext(@NotNull String commitId, @NotNull SegmentedProgressIndicatorManager downloadIndicatorManager,
//                           @NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
//                           @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
//    this.commitId = commitId;
//    this.downloadIndicatorManager = downloadIndicatorManager;
//    this.commitSourcesState = commitSourcesState;
//    this.currentSourcesState = currentSourcesState;
//  }
//
//  @NotNull
//  public String getCommitId() {
//    return commitId;
//  }
//
//  @NotNull
//  public SegmentedProgressIndicatorManager getDownloadIndicatorManager() {
//    return downloadIndicatorManager;
//  }
//
//  @NotNull
//  public Map<String, Map<String, BuildTargetState>> getCommitSourcesState() {
//    return commitSourcesState;
//  }
//
//  @Nullable
//  public Map<String, Map<String, BuildTargetState>> getCurrentSourcesState() {
//    return currentSourcesState;
//  }
//
//  public static JpsLoaderContext createNewContext(@NotNull String commitId, @NotNull SegmentedProgressIndicatorManager downloadIndicatorManager,
//                                           @NotNull Map<String, Map<String, BuildTargetState>> commitSourcesState,
//                                           @Nullable Map<String, Map<String, BuildTargetState>> currentSourcesState) {
//    return new JpsLoaderContext(commitId, downloadIndicatorManager, commitSourcesState, currentSourcesState);
//  }
//}
