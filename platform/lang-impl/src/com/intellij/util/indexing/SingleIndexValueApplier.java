// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@ApiStatus.Internal
final
class SingleIndexValueApplier<FileIndexMetaData> {
  private final FileBasedIndexImpl myIndex;
  @NotNull final ID<?, ?> indexId;
  final int inputId;
  private final @Nullable FileIndexMetaData myFileIndexMetaData;
  final long evaluatingIndexValueApplierTime;
  @NotNull final Supplier<Boolean> storageUpdate;
  @NotNull private final String fileInfo;
  private final boolean isMock;

  SingleIndexValueApplier(@NotNull FileBasedIndexImpl index,
                          @NotNull ID<?, ?> indexId,
                          int inputId,
                          @Nullable FileIndexMetaData fileIndexMetaData,
                          @NotNull Supplier<Boolean> update,
                          @NotNull VirtualFile file,
                          @NotNull FileContent currentFC,
                          long evaluatingIndexValueApplierTime) {
    myIndex = index;
    this.indexId = indexId;
    this.inputId = inputId;
    myFileIndexMetaData = fileIndexMetaData;
    this.evaluatingIndexValueApplierTime = evaluatingIndexValueApplierTime;
    storageUpdate = update;
    fileInfo = FileBasedIndexImpl.getFileInfoLogString(inputId, file, currentFC);
    isMock = FileBasedIndexImpl.isMock(currentFC.getFile());
  }

  boolean wasIndexProvidedByExtension() {
    return storageUpdate instanceof IndexInfrastructureExtensionUpdateComputation &&
           ((IndexInfrastructureExtensionUpdateComputation)storageUpdate).isIndexProvided();
  }

  boolean apply() {
    FileBasedIndexImpl.markFileWritingIndexes(inputId);
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
      if (FileBasedIndexEx.doTraceStubUpdates(indexId) || FileBasedIndexEx.doTraceIndexUpdates()) {
        FileBasedIndexImpl.LOG.info("index " + indexId + " update finished for " + fileInfo);
      }
      if (!isMock) {
        ConcurrencyUtil.withLock(myIndex.myReadLock, () -> {
          //noinspection unchecked
          UpdatableIndex<?, ?, FileContent, FileIndexMetaData> index =
            (UpdatableIndex<?, ?, FileContent, FileIndexMetaData>)myIndex.getIndex(indexId);
          index.setIndexedStateForFileOnFileIndexMetaData(inputId, myFileIndexMetaData, wasIndexProvidedByExtension());
        });
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return "SingleIndexValueApplier{" +
           "indexId=" + indexId +
           ", inputId=" + inputId +
           ", fileInfo='" + fileInfo + '\'' +
           '}';
  }
}
