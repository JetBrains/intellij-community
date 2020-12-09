// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.perFileVersion;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public final class PersistentSubIndexerRetriever<SubIndexerType, SubIndexerVersion> implements Closeable {
  private static final String INDEXED_VERSIONS = "indexed_versions";
  private static final int UNINDEXED_STATE = -2;

  @NotNull
  private final PersistentSubIndexerVersionEnumerator<SubIndexerVersion> myPersistentVersionEnumerator;
  @NotNull
  private final FileAttribute myFileAttribute;
  @NotNull
  private final CompositeDataIndexer<?, ?, SubIndexerType, SubIndexerVersion> myIndexer;

  public PersistentSubIndexerRetriever(@NotNull ID<?, ?> id,
                                int indexVersion,
                                @NotNull CompositeDataIndexer<?, ?, SubIndexerType, SubIndexerVersion> indexer) throws IOException {
    this(IndexInfrastructure.getIndexRootDir(id), id.getName(), indexVersion, indexer);
  }

  @TestOnly
  PersistentSubIndexerRetriever(@NotNull File root,
                                @NotNull String indexName,
                                int indexVersion,
                                @NotNull CompositeDataIndexer<?, ?, SubIndexerType, SubIndexerVersion> indexer) throws IOException {
    Path versionMapRoot = root.toPath().resolve(versionMapRoot());
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

  private void setFileIndexerId(int fileId, int indexerId) throws IOException {
    try (DataOutputStream stream = FSRecords.writeAttribute(fileId, myFileAttribute)) {
      DataInputOutputUtil.writeINT(stream, indexerId);
    }
  }

  public FileIndexingState getSubIndexerState(int fileId, @NotNull IndexedFile file) throws IOException {
    try (DataInputStream stream = FSRecords.readAttributeWithLock(fileId, myFileAttribute)) {
      if (stream != null) {
        int currentIndexedVersion = DataInputOutputUtil.readINT(stream);
        if (currentIndexedVersion == UNINDEXED_STATE) {
          return FileIndexingState.NOT_INDEXED;
        }
        int actualVersion = getFileIndexerId(file);
        return actualVersion == currentIndexedVersion ? FileIndexingState.UP_TO_DATE : FileIndexingState.OUT_DATED;
      }
      return FileIndexingState.NOT_INDEXED;
    }
  }

  public int getFileIndexerId(@NotNull IndexedFile file) throws IOException {
    SubIndexerVersion version = getVersion(file);
    if (version == null) return UNINDEXED_STATE;
    return myPersistentVersionEnumerator.enumerate(version);
  }

  @Nullable
  public SubIndexerVersion getVersion(@NotNull IndexedFile file) {
    SubIndexerType type = myIndexer.calculateSubIndexer(file);
    if (type == null) return null;
    return myIndexer.getSubIndexerVersion(type);
  }

  private static final Map<Pair<String, Integer>, FileAttribute> ourAttributes = new HashMap<>();

  private static FileAttribute getFileAttribute(String name, int version) {
    synchronized (ourAttributes) {
      return ourAttributes.computeIfAbsent(new Pair<>(name, version), __ -> new FileAttribute(name + ".index.version", version, false));
    }
  }
}