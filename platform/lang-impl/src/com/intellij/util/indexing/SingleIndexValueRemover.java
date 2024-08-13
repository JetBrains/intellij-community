// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.indexing.diagnostic.BrokenIndexingDiagnostics;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class SingleIndexValueRemover {
  public final @NotNull ID<?, ?> indexId;

  private final @NotNull FileBasedIndexImpl indexImpl;

  private final int inputId;
  private final @Nullable String fileInfo;
  private final @NotNull FileIndexingResult.ApplicationMode applicationMode;

  /** Time of {@code index.mapInputAndPrepareUpdate(inputId, null)}, in nanoseconds */
  public long evaluatingValueRemoverTime;

  SingleIndexValueRemover(@NotNull FileBasedIndexImpl indexImpl,
                          @NotNull ID<?, ?> indexId,
                          @Nullable VirtualFile file,
                          @Nullable FileContent fileContent,
                          int inputId,
                          @NotNull FileIndexingResult.ApplicationMode applicationMode) {
    this.indexImpl = indexImpl;
    this.indexId = indexId;
    this.inputId = inputId;
    this.fileInfo = FileBasedIndexImpl.getFileInfoLogString(inputId, file, fileContent);
    this.applicationMode = applicationMode;
  }

  /**
   * Contrary to the {@link SingleIndexValueApplier}, the remover does both 'prepare update' and 'apply update to the index'
   * steps here. This is because for removes {@link InvertedIndex#mapInputAndPrepareUpdate(int, Object)} is almost trivial,
   * with ~0 cost.
   * @return false in case index update is not necessary or the update has failed
   */
  public boolean remove() {
    if (!RebuildStatus.isOk(indexId) && !indexImpl.myIsUnitTestMode) {
      return false; // the index is scheduled for rebuild, no need to update
    }
    indexImpl.increaseLocalModCount();

    UpdatableIndex<?, ?, FileContent, ?> index = indexImpl.getIndex(indexId);

    FileBasedIndexImpl.markFileWritingIndexes(inputId);
    try {
      Supplier<Boolean> storageUpdate;
      long startTime = System.nanoTime();
      try {
        storageUpdate = index.mapInputAndPrepareUpdate(inputId, null);
      }
      catch (MapReduceIndexMappingException e) {
        BrokenIndexingDiagnostics.INSTANCE.getExceptionListener().onFileIndexMappingFailed(inputId, null, null, indexId, e);
        return false;
      }
      finally {
        this.evaluatingValueRemoverTime = System.nanoTime() - startTime;
      }

      if (indexImpl.runUpdateForPersistentData(storageUpdate)) {
        if (FileBasedIndexEx.doTraceStubUpdates(indexId) || FileBasedIndexEx.doTraceIndexUpdates()) {
          FileBasedIndexImpl.LOG.info("index " + indexId + " deletion finished for " + fileInfo);
        }
        ConcurrencyUtil.withLock(indexImpl.myReadLock, () -> {
          index.setUnindexedStateForFile(inputId);
        });
      }
      return true;
    }
    catch (RuntimeException exception) {
      indexImpl.requestIndexRebuildOnException(exception, indexId);
      return false;
    }
    finally {
      FileBasedIndexImpl.unmarkWritingIndexes();
    }
  }

  @Override
  public String toString() {
    return "SingleIndexValueRemover{" +
           "indexId=" + indexId +
           ", inputId=" + inputId +
           ", fileInfo='" + fileInfo + '\'' +
           ", applicationMode =" + applicationMode +
           '}';
  }
}
