// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Internal
public final class PersistentSubIndexerRetriever<SubIndexerType, SubIndexerVersion>
  implements Closeable, PersistentSubIndexerRetrieverBase<SubIndexerVersion> {
  private static final String INDEXED_VERSIONS = "indexed_versions";
  private static final int UNINDEXED_STATE = -2;
  private static final int NULL_SUB_INDEXER = -3;

  private final @NotNull PersistentSubIndexerVersionEnumerator<SubIndexerVersion> myPersistentVersionEnumerator;
  private final @NotNull IntFileAttribute myFileAttribute;
  private final @NotNull CompositeDataIndexer<?, ?, SubIndexerType, SubIndexerVersion> myIndexer;

  public PersistentSubIndexerRetriever(@NotNull ID<?, ?> id,
                                int indexVersion,
                                @NotNull CompositeDataIndexer<?, ?, SubIndexerType, SubIndexerVersion> indexer) throws IOException {
    this(IndexInfrastructure.getIndexRootDir(id), id.getName(), indexVersion, indexer);
  }

  @TestOnly
  PersistentSubIndexerRetriever(@NotNull Path root,
                                @NotNull String indexName,
                                int indexVersion,
                                @NotNull CompositeDataIndexer<?, ?, SubIndexerType, SubIndexerVersion> indexer) throws IOException {
    Path versionMapRoot = root.resolve(versionMapRoot());
    myFileAttribute = getFileAttribute(indexName, indexVersion);
    myIndexer = indexer;
    myPersistentVersionEnumerator = new PersistentSubIndexerVersionEnumerator<>(
      versionMapRoot.resolve(INDEXED_VERSIONS).toFile(),
      indexer.getSubIndexerVersionDescriptor());
  }

  public void clear() throws IOException {
    myPersistentVersionEnumerator.clear();
  }

  @Override
  public void close() throws IOException {
    myPersistentVersionEnumerator.close();
  }

  public void flush() throws IOException {
    myPersistentVersionEnumerator.flush();
  }

  public boolean isDirty() {
    return myPersistentVersionEnumerator.isDirty();
  }

  private static Path versionMapRoot() {
    return Paths.get(".perFileVersion", INDEXED_VERSIONS);
  }

  public void setIndexedState(int fileId, @NotNull IndexedFile file) throws IOException {
    int indexerId = ProgressManager.getInstance().computeInNonCancelableSection(() -> getFileIndexerId(file));
    setFileIndexerId(fileId, indexerId);
  }

  public void setUnindexedState(int fileId) throws IOException {
    setFileIndexerId(fileId, UNINDEXED_STATE);
  }

  public void setFileIndexerId(int fileId, int indexerId) throws IOException {
    myFileAttribute.writeInt(fileId, indexerId);
  }

  /**
   * @return stored file indexer id. value < 0 means that no id is available for specified file
   */
  public int getStoredFileIndexerId(int fileId) throws IOException {
    int indexerId = myFileAttribute.readInt(fileId);
    return indexerId == 0 ? UNINDEXED_STATE : indexerId;
  }

  public FileIndexingState getSubIndexerState(int fileId, @NotNull IndexedFile file) throws IOException {
    int indexerId = myFileAttribute.readInt(fileId);
    if (indexerId == 0) {
      return FileIndexingState.OUT_DATED;
    } else if (indexerId == UNINDEXED_STATE) {
      return FileIndexingState.NOT_INDEXED;
    } else {
      int actualVersion = getFileIndexerId(file);
      return actualVersion == indexerId ? FileIndexingState.UP_TO_DATE : FileIndexingState.OUT_DATED;
    }
  }

  @Override
  public int getFileIndexerId(@NotNull IndexedFile file) throws IOException {
    SubIndexerVersion version = getVersion(file);
    if (version == null) return NULL_SUB_INDEXER;
    return myPersistentVersionEnumerator.enumerate(version);
  }

  public SubIndexerVersion getVersionByIndexerId(int indexerId) throws IOException {
    return myPersistentVersionEnumerator.valueOf(indexerId);
  }

  @Override
  public @Nullable SubIndexerVersion getVersion(@NotNull IndexedFile file) {
    SubIndexerType type = myIndexer.calculateSubIndexer(file);
    if (type == null) return null;
    return myIndexer.getSubIndexerVersion(type);
  }

  private static final Map<Pair<String, Integer>, IntFileAttribute> ourAttributes = new HashMap<>();

  private static IntFileAttribute getFileAttribute(String name, int version) {
    synchronized (ourAttributes) {
      return ourAttributes.computeIfAbsent(new Pair<>(name, version), __ -> {
        return IntFileAttribute.create(name + ".index.version", version);
      });
    }
  }
}