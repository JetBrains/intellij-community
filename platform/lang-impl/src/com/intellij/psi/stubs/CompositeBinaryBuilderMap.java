// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.indexing.FileIndexingState;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

final class CompositeBinaryBuilderMap {
  private static final Logger LOG = Logger.getInstance(CompositeBinaryBuilderMap.class);
  private static final FileAttribute VERSION_STAMP = new FileAttribute("stubIndex.cumulativeBinaryBuilder", 1, true);

  private final Object2IntMap<FileType> myCumulativeVersionMap;

  CompositeBinaryBuilderMap() throws IOException {
    try (PersistentStringEnumerator cumulativeVersionEnumerator = new PersistentStringEnumerator(registeredCompositeBinaryBuilderFiles())) {
      myCumulativeVersionMap = new Object2IntOpenHashMap<>();

      for (Map.Entry<FileType, BinaryFileStubBuilder> entry : BinaryFileStubBuilders.INSTANCE.getAllRegisteredExtensions().entrySet()) {
        FileType fileType = entry.getKey();
        BinaryFileStubBuilder builder = entry.getValue();
        if (!(builder instanceof BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<?>)) {
          continue;
        }

        StringBuilder cumulativeVersion = new StringBuilder();
        cumulativeVersion.append(fileType.getName()).append("->").append(builder.getClass().getName()).append(':').append(builder.getStubVersion());
        @SuppressWarnings({"unchecked", "rawtypes"})
        BinaryFileStubBuilder.CompositeBinaryFileStubBuilder<Object> compositeBuilder =
          (BinaryFileStubBuilder.CompositeBinaryFileStubBuilder)builder;
        cumulativeVersion.append(";");
        cumulativeVersion.append(compositeBuilder.getAllSubBuilders().map(b -> compositeBuilder.getSubBuilderVersion(b)).sorted().collect(Collectors.joining(";")));

        int enumeratedId = cumulativeVersionEnumerator.enumerate(cumulativeVersion.toString());
        LOG.debug("composite binary stub builder for " + fileType + " registered:  " +
                  "id = " + enumeratedId + ", " +
                  "value" + cumulativeVersion);
        myCumulativeVersionMap.put(fileType, enumeratedId);
      }
    }
  }

  void persistState(int fileId, @NotNull VirtualFile file) throws IOException {
    int version = getBuilderCumulativeVersion(file);
    persistVersion(version, fileId);
  }

  private void persistVersion(int version, int fileId) throws IOException {
    if (version == 0) return;
    try (DataOutputStream stream = FSRecords.writeAttribute(fileId, VERSION_STAMP)) {
      DataInputOutputUtil.writeINT(stream, version);
    }
  }

  void persistState(int fileId, @NotNull FileType fileType) throws IOException {
    int version = myCumulativeVersionMap.getInt(fileType);
    persistVersion(version, fileId);
  }

  void resetPersistedState(int fileId) throws IOException {
    boolean hasWrittenAttribute;
    try (DataInputStream stream = FSRecords.readAttributeWithLock(fileId, VERSION_STAMP)) {
      hasWrittenAttribute = stream != null;
    }
    if (hasWrittenAttribute) {
      try (DataOutputStream stream = FSRecords.writeAttribute(fileId, VERSION_STAMP)) {
        DataInputOutputUtil.writeINT(stream, 0);
      }
    }
  }

  FileIndexingState isUpToDateState(int fileId, @NotNull VirtualFile file) throws IOException {
    int indexedVersion = 0;
    try (DataInputStream stream = FSRecords.readAttributeWithLock(fileId, VERSION_STAMP)) {
      if (stream != null) {
        indexedVersion = DataInputOutputUtil.readINT(stream);
      }
    }

    if (indexedVersion == 0) {
      return FileIndexingState.NOT_INDEXED;
    }

    int actualVersion = getBuilderCumulativeVersion(file);
    return actualVersion == indexedVersion ? FileIndexingState.UP_TO_DATE : FileIndexingState.OUT_DATED;
  }

  private int getBuilderCumulativeVersion(@NotNull VirtualFile file) {
    FileType type = ProgressManager.getInstance().computeInNonCancelableSection(() -> file.getFileType());
    return myCumulativeVersionMap.getInt(type);
  }

  private static @NotNull Path registeredCompositeBinaryBuilderFiles() throws IOException {
    return IndexInfrastructure.getIndexRootDir(StubUpdatingIndex.INDEX_ID).resolve(".binary_builders");
  }
}