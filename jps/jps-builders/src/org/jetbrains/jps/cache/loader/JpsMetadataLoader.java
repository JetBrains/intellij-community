// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cache.loader;

import com.google.gson.stream.JsonReader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cache.client.JpsNettyClient;
import org.jetbrains.jps.cache.client.JpsServerClient;
import org.jetbrains.jps.cache.model.BuildTargetState;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.storage.BuildTargetSourcesState;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class JpsMetadataLoader {
  private static final Logger LOG = Logger.getInstance(JpsMetadataLoader.class);
  private final Path sourceStateFile;
  private final JpsServerClient client;

  public JpsMetadataLoader(@NotNull String projectPath, @NotNull JpsServerClient client) {
    this.client = client;
    sourceStateFile = Utils.getDataStorageRoot(projectPath).toPath().resolve(BuildTargetSourcesState.TARGET_SOURCES_STATE_FILE_NAME);
  }

  @Nullable
  Map<String, Map<String, BuildTargetState>> loadMetadataForCommit(@NotNull JpsNettyClient nettyClient, @NotNull String metadataId) {
    // On server commitId is the same as metadataId this data simply located in different folders
    Path tmpFolder = Path.of(PathManager.getTempPath());
    Path metadataFile = client.downloadMetadataById(nettyClient, metadataId, tmpFolder);
    if (metadataFile == null) {
      return null;
    }

    try (BufferedReader bufferedReader = Files.newBufferedReader(metadataFile)) {
      return BuildTargetSourcesState.readJson(new JsonReader(bufferedReader));
    }
    catch (IOException e) {
      LOG.warn("Couldn't parse content of file " + metadataFile, e);
    }
    finally {
      try {
        Files.deleteIfExists(metadataFile);
      }
      catch (IOException ignore) {
      }
    }
    return null;
  }

  @Nullable
  Map<String, Map<String, BuildTargetState>> loadCurrentProjectMetadata() {
    if (Files.notExists(sourceStateFile)) {
      return null;
    }

    try (BufferedReader bufferedReader = Files.newBufferedReader(sourceStateFile)) {
      return BuildTargetSourcesState.readJson(new JsonReader(bufferedReader));
    }
    catch (IOException e) {
      LOG.warn("Couldn't parse current project metadata " + sourceStateFile, e);
    }
    return null;
  }

  void dropCurrentProjectMetadata() {
    try {
      Files.deleteIfExists(sourceStateFile);
    }
    catch (IOException ignore) {
    }
  }
}
