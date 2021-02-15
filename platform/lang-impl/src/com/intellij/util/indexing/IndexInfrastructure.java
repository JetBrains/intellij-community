// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class IndexInfrastructure {
  private static final String STUB_VERSIONS = ".versions";
  private static final String PERSISTENT_INDEX_DIRECTORY_NAME = ".persistent";

  private IndexInfrastructure() {
  }

  @NotNull
  public static File getVersionFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexDirectory(indexName, true), indexName + ".ver");
  }

  @NotNull
  public static File getStorageFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName.getName());
  }

  @NotNull
  public static File getInputIndexStorageFile(@NotNull ID<?, ?> indexName) {
    return new File(getIndexRootDir(indexName), indexName +"_inputs");
  }

  @NotNull
  public static File getIndexRootDir(@NotNull ID<?, ?> indexName) {
    return getIndexDirectory(indexName, false);
  }

  public static File getPersistentIndexRoot() {
    File indexDir = new File(PathManager.getIndexRoot() + File.separator + PERSISTENT_INDEX_DIRECTORY_NAME);
    indexDir.mkdirs();
    return indexDir;
  }

  @NotNull
  public static File getPersistentIndexRootDir(@NotNull ID<?, ?> indexName) {
    return getIndexDirectory(indexName, false, PERSISTENT_INDEX_DIRECTORY_NAME);
  }

  @NotNull
  private static File getIndexDirectory(@NotNull ID<?, ?> indexName, boolean forVersion) {
    return getIndexDirectory(indexName, forVersion, "");
  }

  @NotNull
  private static File getIndexDirectory(@NotNull ID<?, ?> indexId, boolean forVersion, String relativePath) {
    return getIndexDirectory(indexId.getName(), relativePath, indexId instanceof StubIndexKey, forVersion);
  }

  @NotNull
  private static File getIndexDirectory(String indexName, String relativePath, boolean stubKey, boolean forVersion) {
    indexName = StringUtil.toLowerCase(indexName);
    File indexDir;
    if (stubKey) {
      // store StubIndices under StubUpdating index' root to ensure they are deleted
      // when StubUpdatingIndex version is changed
      indexDir = new File(getIndexDirectory(StubUpdatingIndex.INDEX_ID, false, relativePath), forVersion ? STUB_VERSIONS : indexName);
    } else {
      if (relativePath.length() > 0) relativePath = File.separator + relativePath;
      indexDir = new File(PathManager.getIndexRoot() + relativePath, indexName);
    }
    if (!FileBasedIndex.USE_IN_MEMORY_INDEX) {
      // TODO should be created automatically with storages
      indexDir.mkdirs();
    }
    return indexDir;
  }

  @ApiStatus.Internal
  public static File getFileBasedIndexRootDir(@NotNull String indexName) {
    return getIndexDirectory(indexName, "", false, false);
  }

  @ApiStatus.Internal
  public static File getStubIndexRootDir(@NotNull String indexName) {
    return getIndexDirectory(indexName, "", true, false);
  }

  public static boolean hasIndices() {
    return !SystemProperties.is("idea.skip.indices.initialization");
  }

  public static boolean isIndexesInitializationSuspended() {
    return SystemProperties.is("idea.suspend.indexes.initialization");
  }
}