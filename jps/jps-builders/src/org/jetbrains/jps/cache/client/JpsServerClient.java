package org.jetbrains.jps.cache.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.cache.model.AffectedModule;
import org.jetbrains.jps.cache.model.JpsLoaderContext;
import org.jetbrains.jps.cache.model.OutputLoadResult;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public interface JpsServerClient {
  @Nullable Path downloadMetadataById(@NotNull JpsNettyClient nettyClient, @NotNull String metadataId, @NotNull Path targetDir);

  File downloadCacheById(@NotNull JpsLoaderContext context, @NotNull String cacheId, @NotNull File targetDir);

  @Unmodifiable
  List<OutputLoadResult> downloadCompiledModules(@NotNull JpsLoaderContext context, @NotNull List<AffectedModule> affectedModules);

  static JpsServerClient getServerClient(@NotNull String serverUrl) {
    return new JpsServerClientImpl(serverUrl);
  }
}