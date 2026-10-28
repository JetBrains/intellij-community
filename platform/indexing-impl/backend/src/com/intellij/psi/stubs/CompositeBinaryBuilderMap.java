// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileIndexingStateWithExplanation;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.indexing.impl.perFileVersion.IntFileAttribute;
import com.intellij.util.io.PersistentStringEnumerator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static java.util.stream.Collectors.joining;

final class CompositeBinaryBuilderMap implements Closeable {
  private static final Logger LOG = Logger.getInstance(CompositeBinaryBuilderMap.class);

  /** Must be a single instance of IntFileAttribute per id */
  private final static IntFileAttribute VERSIONS_STORAGE = IntFileAttribute.create("stubIndex.cumulativeBinaryBuilder", /*version: */ 1);

  private final IntFileAttribute versionsStorage;

  private final Object2IntMap<FileType> myCumulativeVersionMap;

  /** @param useDummyAttributes don't touch persistence, just emulate behavior */
  CompositeBinaryBuilderMap(boolean useDummyAttributes) throws IOException {
    myCumulativeVersionMap = new Object2IntOpenHashMap<>();
    if (useDummyAttributes) {
      versionsStorage = IntFileAttribute.dummyAttribute();
    }
    else {
      try (PersistentStringEnumerator cumulativeVersionEnumerator = new PersistentStringEnumerator(registeredCompositeBinaryBuilderFiles())) {
        for (Map.Entry<FileType, BinaryFileStubBuilder> entry : BinaryFileStubBuilders.INSTANCE.getAllRegisteredExtensions().entrySet()) {
          FileType fileType = entry.getKey();
          BinaryFileStubBuilder builder = entry.getValue();
          if (!(builder instanceof BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<?>)) {
            continue;
          }
          @SuppressWarnings("unchecked")
          BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<Object> compositeBuilder =
            (BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<Object>)builder;

          StringBuilder cumulativeVersion = new StringBuilder();
          cumulativeVersion.append(fileType.getName())
            .append("->").append(builder.getClass().getName())
            .append(':').append(builder.getStubVersion());

          cumulativeVersion.append(";");
          cumulativeVersion.append(
            compositeBuilder.getAllSubBuilders()
              .map(compositeBuilder::getSubBuilderVersion)
              .sorted()
              .collect(joining(";"))
          );

          int enumeratedId = cumulativeVersionEnumerator.enumerate(cumulativeVersion.toString());
          LOG.debug("composite binary stub builder for " + fileType + " registered:  " +
                    "id = " + enumeratedId + ", " +
                    "value" + cumulativeVersion);
          myCumulativeVersionMap.put(fileType, enumeratedId);
        }
      }
      versionsStorage = VERSIONS_STORAGE;
    }
  }

  void persistState(int fileId, @NotNull VirtualFile file) {
    int version = getBuilderCumulativeVersion(file);
    persistVersion(version, fileId);
  }

  private void persistVersion(int version, int fileId) {
    if (version == 0) return;
    try {
      versionsStorage.writeInt(fileId, version);
    }
    catch (IOException e) {
      LOG.error("Can't persistVersion(#" + fileId + " = " + version + ")", e);
    }
  }

  void persistState(int fileId, @NotNull FileType fileType) {
    int version = myCumulativeVersionMap.getInt(fileType);
    persistVersion(version, fileId);
  }

  void resetPersistedState(int fileId) {
    try {
      versionsStorage.writeInt(fileId, 0);
    }
    catch (IOException e) {
      LOG.error("Can't resetPersistedState(#" + fileId + ")", e);
    }
  }

  @Override
  public void close() throws IOException {
    //since it is AutoRefreshingOnVfsCloseRef under the hood, the storage will be automatically re-opened on next access
    versionsStorage.close();
  }

  FileIndexingStateWithExplanation isUpToDateState(int fileId, @NotNull VirtualFile file) {
    int indexedVersion;
    try {
      indexedVersion = versionsStorage.readInt(fileId);
    }
    catch (IOException e) {
      LOG.error(e);
      return FileIndexingStateWithExplanation.outdated("IOException: " + e.getMessage());
    }

    if (indexedVersion == 0) {
      return FileIndexingStateWithExplanation.notIndexed();
    }

    int actualVersion = getBuilderCumulativeVersion(file);
    if (actualVersion == indexedVersion) {
      return FileIndexingStateWithExplanation.upToDate();
    }

    return FileIndexingStateWithExplanation.outdated(
      () -> "actual version (" + actualVersion + ") != indexedVersion (" + indexedVersion + ")"
    );
  }

  private int getBuilderCumulativeVersion(@NotNull VirtualFile file) {
    FileType type = ProgressManager.getInstance().computeInNonCancelableSection(file::getFileType);
    return myCumulativeVersionMap.getInt(type);
  }

  private static @NotNull Path registeredCompositeBinaryBuilderFiles() throws IOException {
    return IndexInfrastructure.getIndexRootDir(StubUpdatingIndex.INDEX_ID).resolve(".binary_builders");
  }
}