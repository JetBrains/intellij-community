package org.jetbrains.jps.cache.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cache.model.AffectedModule;
import org.jetbrains.jps.cache.model.JpsLoaderContext;
import org.jetbrains.jps.cache.model.OutputLoadResult;
import org.jetbrains.jps.cache.ui.SegmentedProgressIndicatorManager;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface JpsServerClient {
  @NotNull
  Map<String, Set<String>> getCacheKeysPerRemote();
  @Nullable
  File downloadMetadataById(@NotNull JpsNettyClient nettyClient, @NotNull String metadataId, @NotNull File targetDir);
  File downloadCacheById(@NotNull JpsLoaderContext context, @NotNull String cacheId, @NotNull File targetDir);
  List<OutputLoadResult> downloadCompiledModules(@NotNull JpsLoaderContext context, @NotNull List<AffectedModule> affectedModules);
  static JpsServerClient getServerClient() {
    return JpsServerClientImpl.INSTANCE;
  }
}