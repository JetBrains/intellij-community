// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.stubs.StubUpdatingIndex;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IndexInfrastructure {
  private static final String STUB_VERSIONS = ".versions";
  private static final String PERSISTENT_INDEX_DIRECTORY_NAME = ".persistent";

  private IndexInfrastructure() {
  }

  public static @NotNull Path getVersionFile(@NotNull ID<?, ?> indexName) throws IOException {
    return getIndexDirectory(indexName, true).resolve(indexName + ".ver");
  }

  public static @NotNull Path getStorageFile(@NotNull ID<?, ?> indexName) throws IOException {
    return getIndexRootDir(indexName).resolve(indexName.getName());
  }

  public static @NotNull Path getInputIndexStorageFile(@NotNull ID<?, ?> indexName) throws IOException {
    return getIndexRootDir(indexName).resolve(indexName + "_inputs");
  }

  public static @NotNull Path getIndexRootDir(@NotNull ID<?, ?> indexName) throws IOException {
    return getIndexDirectory(indexName, false);
  }

  public static @NotNull Path getPersistentIndexRoot() throws IOException {
    Path indexDir = PathManager.getIndexRoot().resolve(PERSISTENT_INDEX_DIRECTORY_NAME);
    Files.createDirectories(indexDir);
    return indexDir;
  }

  public static @NotNull Path getPersistentIndexRootDir(@NotNull ID<?, ?> indexName) throws IOException {
    return getIndexDirectory(indexName, false, PERSISTENT_INDEX_DIRECTORY_NAME);
  }

  private static @NotNull Path getIndexDirectory(@NotNull ID<?, ?> indexName, boolean forVersion) throws IOException {
    return getIndexDirectory(indexName, forVersion, "");
  }

  private static @NotNull Path getIndexDirectory(@NotNull ID<?, ?> indexId, boolean forVersion, String relativePath) throws IOException {
    return getIndexDirectory(indexId.getName(), relativePath, indexId instanceof StubIndexKey, forVersion);
  }

  private static @NotNull Path getIndexDirectory(String indexName, String relativePath, boolean stubKey, boolean forVersion) throws IOException {
    indexName = Strings.toLowerCase(indexName);
    Path indexDir;
    if (stubKey) {
      // store StubIndices under StubUpdating index' root to ensure they are deleted
      // when StubUpdatingIndex version is changed
      indexDir = getIndexDirectory(StubUpdatingIndex.INDEX_ID, false, relativePath).resolve(forVersion ? STUB_VERSIONS : indexName);
    }
    else {
      indexDir = PathManager.getIndexRoot();
      if (!relativePath.isEmpty()) {
        indexDir = indexDir.resolve(relativePath);
      }
      indexDir = indexDir.resolve(indexName);
    }
    if (!FileBasedIndex.USE_IN_MEMORY_INDEX) {
      // TODO should be created automatically with storages
      Files.createDirectories(indexDir);
    }
    return indexDir;
  }

  @ApiStatus.Internal
  public static @NotNull Path getFileBasedIndexRootDir(@NotNull String indexName) throws IOException {
    return getIndexDirectory(indexName, "", false, false);
  }

  @ApiStatus.Internal
  public static @NotNull Path getStubIndexRootDir(@NotNull String indexName) throws IOException {
    return getIndexDirectory(indexName, "", true, false);
  }

  public static boolean hasIndices() {
    return !Boolean.getBoolean("idea.skip.indices.initialization");
  }

  public static boolean isIndexesInitializationSuspended() {
    return Boolean.getBoolean("idea.suspend.indexes.initialization");
  }
}
