// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

@ApiStatus.Internal
class SingleIndexValueApplier<CachedFileData> {
  private final FileBasedIndexImpl myIndex;
  @NotNull final ID<?, ?> indexId;
  final int inputId;
  private final CachedFileData myCachedFileData;
  final long mapInputTime;
  @NotNull final Supplier<Boolean> storageUpdate;
  @NotNull private final String fileInfo;
  @NotNull private final String filePath;
  private final boolean isMock;

  SingleIndexValueApplier(FileBasedIndexImpl index, @NotNull ID<?, ?> indexId,
                          int inputId,
                          @NotNull CachedFileData cachedFileData,
                          @NotNull Supplier<Boolean> update,
                          @NotNull VirtualFile file,
                          @NotNull FileContent currentFC,
                          long mapInputTime) {
    myIndex = index;
    this.indexId = indexId;
    this.inputId = inputId;
    myCachedFileData = cachedFileData;
    this.mapInputTime = mapInputTime;
    storageUpdate = update;
    fileInfo = FileBasedIndexImpl.getFileInfoLogString(inputId, file, currentFC);
    filePath = file.getPath();
    isMock = FileBasedIndexImpl.isMock(currentFC.getFile());
  }

  boolean wasIndexProvidedByExtension() {
    return storageUpdate instanceof IndexInfrastructureExtensionUpdateComputation &&
           ((IndexInfrastructureExtensionUpdateComputation)storageUpdate).isIndexProvided();
  }

  boolean applyImmediately() {
    return doApply();
  }

  boolean apply() {
    FileBasedIndexImpl.markFileWritingIndexes(inputId, filePath);
    try {
      return doApply();
    }
    catch (RuntimeException exception) {
      myIndex.requestIndexRebuildOnException(exception, indexId);
      return false;
    }
    finally {
      FileBasedIndexImpl.unmarkWritingIndexes();
    }
  }

  private boolean doApply() {
    if (myIndex.runUpdateForPersistentData(storageUpdate)) {
      if (myIndex.doTraceStubUpdates(indexId) || myIndex.doTraceIndexUpdates()) {
        FileBasedIndexImpl.LOG.info("index " + indexId + " update finished for " + fileInfo);
      }
      if (!isMock) {
        ConcurrencyUtil.withLock(myIndex.myReadLock, () -> {
          //noinspection unchecked
          UpdatableIndex<?, ?, FileContent, CachedFileData> index =
            (UpdatableIndex<?, ?, FileContent, CachedFileData>)myIndex.getIndex(indexId);
          setIndexedState(index, myCachedFileData, inputId, wasIndexProvidedByExtension());
        });
      }
    }
    return true;
  }

  private static <CachedFileData> void setIndexedState(UpdatableIndex<?, ?, FileContent, CachedFileData> index,
                                                       @NotNull CachedFileData fileData,
                                                       int inputId,
                                                       boolean indexWasProvided) {
    if (index instanceof FileBasedIndexInfrastructureExtensionUpdatableIndex) {
      //noinspection unchecked
      ((FileBasedIndexInfrastructureExtensionUpdatableIndex<?, ?, ?, CachedFileData>)index)
        .setIndexedStateForFileOnCachedData(inputId, fileData, indexWasProvided);
    }
    else {
      index.setIndexedStateForFileOnCachedData(inputId, fileData);
    }
  }
}
