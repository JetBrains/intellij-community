// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.indexing.diagnostic.BrokenIndexingDiagnostics;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.indexing.snapshot.SnapshotInputMappingException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@ApiStatus.Internal
class SingleIndexValueRemover {
  private final FileBasedIndexImpl myIndexImpl;
  final @NotNull ID<?, ?> indexId;
  private final VirtualFile file;
  private final int inputId;
  private final @Nullable String fileInfo;
  private final boolean isWritingValuesSeparately;
  long evaluatingValueRemoverTime;

  SingleIndexValueRemover(FileBasedIndexImpl indexImpl, @NotNull ID<?, ?> indexId,
                          @Nullable VirtualFile file,
                          @Nullable FileContent fileContent,
                          int inputId,
                          boolean isWritingValuesSeparately) {
    myIndexImpl = indexImpl;
    this.indexId = indexId;
    this.file = file;
    this.inputId = inputId;
    this.fileInfo = FileBasedIndexImpl.getFileInfoLogString(inputId, file, fileContent);
    this.isWritingValuesSeparately = isWritingValuesSeparately;
  }

  /**
   * @return false in case index update is not necessary or the update has failed
   */
  boolean remove() {
    if (!RebuildStatus.isOk(indexId) && !myIndexImpl.myIsUnitTestMode) {
      return false; // the index is scheduled for rebuild, no need to update
    }
    myIndexImpl.increaseLocalModCount();

    UpdatableIndex<?, ?, FileContent, ?> index = myIndexImpl.getIndex(indexId);

    if (isWritingValuesSeparately) {
      FileBasedIndexImpl.markFileWritingIndexes(inputId);
    }
    else {
      FileBasedIndexImpl.markFileIndexed(file, null);
    }
    try {
      Supplier<Boolean> storageUpdate;
      long startTime = System.nanoTime();
      try {
        storageUpdate = index.mapInputAndPrepareUpdate(inputId, null);
      }
      catch (MapReduceIndexMappingException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SnapshotInputMappingException) {
          myIndexImpl.requestRebuild(indexId, e);
          return false;
        }
        BrokenIndexingDiagnostics.INSTANCE.getExceptionListener().onFileIndexMappingFailed(inputId, null, null, indexId, e);
        return false;
      }
      finally {
        this.evaluatingValueRemoverTime = System.nanoTime() - startTime;
      }

      if (myIndexImpl.runUpdateForPersistentData(storageUpdate)) {
        if (myIndexImpl.doTraceStubUpdates(indexId) || myIndexImpl.doTraceIndexUpdates()) {
          FileBasedIndexImpl.LOG.info("index " + indexId + " deletion finished for " + fileInfo);
        }
        ConcurrencyUtil.withLock(myIndexImpl.myReadLock, () -> {
          index.setUnindexedStateForFile(inputId);
        });
      }
      return true;
    }
    catch (RuntimeException exception) {
      myIndexImpl.requestIndexRebuildOnException(exception, indexId);
      return false;
    }
    finally {
      if (isWritingValuesSeparately) {
        FileBasedIndexImpl.unmarkWritingIndexes();
      }
      else {
        FileBasedIndexImpl.unmarkBeingIndexed();
      }
    }
  }

  @Override
  public String toString() {
    return "SingleIndexValueRemover{" +
           "indexId=" + indexId +
           ", inputId=" + inputId +
           ", fileInfo='" + fileInfo + '\'' +
           ", isWritingValuesSeparately=" + isWritingValuesSeparately +
           '}';
  }
}
