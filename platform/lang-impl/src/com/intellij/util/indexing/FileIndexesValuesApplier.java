// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsData;
import com.intellij.util.indexing.contentQueue.IndexUpdateWriter;
import com.intellij.util.indexing.dependencies.FileIndexingStamp;
import com.intellij.util.indexing.diagnostic.FileIndexingStatistics;
import com.intellij.util.indexing.diagnostic.IndexesEvaluated;
import com.intellij.util.indexing.events.VfsEventsMerger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.util.indexing.contentQueue.IndexUpdateWriter.*;

@ApiStatus.Internal
public final class FileIndexesValuesApplier {
  /**
   * Initially, index values were applied synchronously under the same read lock they were counted.
   * Later there was introduced an optimization to apply (or erase if needed) them later outside read lock.
   * Then application on separate threads became available.
   * And then the single-thread-under-read-lock application was <a href="https://youtrack.jetbrains.com/issue/IDEA-332132">removed</a> as unused.
   */
  public enum ApplicationMode {
    SameThreadOutsideReadLock, AnotherThread
  }
  private final FileBasedIndexImpl myIndex;
  private final int fileId;
  private final @NotNull FileIndexingStamp indexingStamp;
  private final @NotNull List<? extends SingleIndexValueApplier<?>> appliers;
  private final @NotNull List<SingleIndexValueRemover> removers;
  private final boolean removeDataFromIndicesForFile;
  private final boolean shouldMarkFileAsIndexed;
  private final long fileStatusLockObject;
  @NotNull
  public final FileIndexingStatistics stats;

  // Use getApplicationMode() instead
  private final ApplicationMode _initialApplicationMode;
  private final AtomicLong separateApplicationTimeNanos = new AtomicLong(-1);
  private boolean wasForcedToApplyOnTheSameThread = false;

  FileIndexesValuesApplier(FileBasedIndexImpl index, int fileId,
                           @NotNull VirtualFile file,
                           @NotNull FileIndexingStamp indexingStamp,
                           @NotNull List<? extends SingleIndexValueApplier<?>> appliers,
                           @NotNull List<SingleIndexValueRemover> removers,
                           boolean removeDataFromIndicesForFile,
                           boolean shouldMarkFileAsIndexed,
                           @NotNull ApplicationMode applicationMode,
                           @NotNull FileType fileType,
                           boolean logEmptyProvidedIndexes) {
    myIndex = index;
    this.fileId = fileId;
    this.indexingStamp = indexingStamp;
    this.appliers = appliers;
    this.removers = removers;
    this.removeDataFromIndicesForFile = removeDataFromIndicesForFile;
    this.shouldMarkFileAsIndexed = shouldMarkFileAsIndexed;
    fileStatusLockObject = shouldMarkFileAsIndexed && !VfsData.isIndexedFlagDisabled()
                           ? IndexingFlag.getOrCreateHash(file)
                           : IndexingFlag.getNonExistentHash();
    this._initialApplicationMode = applicationMode;
    stats = createStats(file, appliers, removers, fileType, logEmptyProvidedIndexes);
  }

  @NotNull
  public ApplicationMode getApplicationMode() {
    if (wasForcedToApplyOnTheSameThread && _initialApplicationMode == ApplicationMode.AnotherThread) {
      //it's usually too late to try to apply under the same read lock
      return ApplicationMode.SameThreadOutsideReadLock;
    }
    return _initialApplicationMode;
  }

  private FileIndexingStatistics createStats(@NotNull VirtualFile file,
                                             @NotNull List<? extends SingleIndexValueApplier<?>> appliers,
                                             @NotNull List<SingleIndexValueRemover> removers,
                                             @NotNull FileType fileType,
                                             boolean logEmptyProvidedIndexes) {
    Set<ID<?, ?>> indexesProvidedByExtensions = new HashSet<>();
    boolean wasFullyIndexedByInfrastructureExtension = true;
    Map<ID<?, ?>, Long> perIndexerEvaluatingIndexValueAppliersTimes = new HashMap<>();
    for (SingleIndexValueApplier<?> applier : appliers) {
      perIndexerEvaluatingIndexValueAppliersTimes.put(applier.indexId, applier.evaluatingIndexValueApplierTime);
      if (applier.wasIndexProvidedByExtension()) {
        indexesProvidedByExtensions.add(applier.indexId);
      }
      else {
        if (FileBasedIndexEx.doTraceSharedIndexUpdates()) {
          FileBasedIndexImpl.LOG.info("shared index " + applier.indexId + " is not provided for file " + file.getName());
        }
        wasFullyIndexedByInfrastructureExtension = false;
      }
    }
    Map<ID<?, ?>, Long> perIndexerEvaluatingIndexValueRemoversTimes = new HashMap<>();
    for (SingleIndexValueRemover remover : removers) {
      perIndexerEvaluatingIndexValueRemoversTimes.put(remover.indexId,
                                                      remover.evaluatingValueRemoverTime); //is not 0 only when !writeIndexSeparately
    }
    if (logEmptyProvidedIndexes && indexesProvidedByExtensions.isEmpty()) {
      FileBasedIndexImpl.LOG.info("no shared indexes were provided for file " + file.getName());
    }

    final IndexesEvaluated indexesEvaluated;
    if (wasFullyIndexedByInfrastructureExtension && !indexesProvidedByExtensions.isEmpty()) {
      indexesEvaluated = IndexesEvaluated.BY_EXTENSIONS;
    }
    else if (appliers.isEmpty() && removers.isEmpty() && !removeDataFromIndicesForFile) {
      indexesEvaluated = IndexesEvaluated.NOTHING_TO_WRITE;
    }
    else {
      indexesEvaluated = IndexesEvaluated.BY_USUAL_INDEXES;
    }

    return new FileIndexingStatistics(fileType,
                                      indexesProvidedByExtensions,
                                      perIndexerEvaluatingIndexValueAppliersTimes,
                                      perIndexerEvaluatingIndexValueRemoversTimes,
                                      indexesEvaluated);
  }

  /**
   * Apply the applier in the same or separate thread and runs {@code callback} in the end on any outcome
   */
  public void apply(@NotNull VirtualFile file, @Nullable Runnable callback, boolean forceApplicationOnSameThread) {
    this.wasForcedToApplyOnTheSameThread = forceApplicationOnSameThread;
    long startTime = System.nanoTime();
    if (removeDataFromIndicesForFile) {
      myIndex.removeDataFromIndicesForFile(fileId, file, "invalid_or_large_file");
    }

    if (appliers.isEmpty() && removers.isEmpty()) {
      doPostModificationJob(file, true, "empty appliers");
      separateApplicationTimeNanos.set(System.nanoTime() - startTime);
      if (callback != null) {
        callback.run();
      }
      return;
    }

    if (getApplicationMode() == ApplicationMode.SameThreadOutsideReadLock) {
      separateApplicationTimeNanos.set(System.nanoTime() - startTime);
      applyModifications(file, -1, null, callback, "same thread");
      return;
    }

    BitSet executorsToSchedule = new BitSet(TOTAL_WRITERS_NUMBER);

    for (SingleIndexValueApplier<?> applier : appliers) {
      executorsToSchedule.set(getExecutorIndex(applier.indexId));
    }

    for (SingleIndexValueRemover remover : removers) {
      executorsToSchedule.set(getExecutorIndex(remover.indexId));
    }

    // Schedule appliers to dedicated executors
    AtomicInteger syncCounter = new AtomicInteger(executorsToSchedule.cardinality());
    separateApplicationTimeNanos.set(0);

    for (int i = 0; i < TOTAL_WRITERS_NUMBER; i++) {
      if (executorsToSchedule.get(i)) {
        var executorIndex = i;
        scheduleIndexWriting(executorIndex,
                             () -> applyModifications(file, executorIndex, syncCounter, callback, "executor " + executorIndex));
      }
    }

    separateApplicationTimeNanos.addAndGet(System.nanoTime() - startTime);
  }

  private void doPostModificationJob(@NotNull VirtualFile file, boolean allModificationsSuccessful, @NotNull String debugString) {
    if (allModificationsSuccessful) {
      VfsEventsMerger.tryLog("INDEX_UPDATED", file,
                             () -> " updated_indexes=" + stats.getPerIndexerEvaluateIndexValueTimes().keySet() +
                                   " deleted_indexes=" + stats.getPerIndexerEvaluatingIndexValueRemoversTimes().keySet() +
                                   " " + debugString);
      myIndex.getChangedFilesCollector().removeFileIdFromFilesScheduledForUpdate(fileId);

      if (shouldMarkFileAsIndexed) {
        IndexingFlag.setIndexedIfFileWithSameLock(file, fileStatusLockObject, indexingStamp);
      }
      else if (fileStatusLockObject != IndexingFlag.getNonExistentHash()) {
        IndexingFlag.unlockFile(file);
      }
    }
    else {
      VfsEventsMerger.tryLog("INDEX_PARTIAL_UPDATE", file, () -> debugString);
      if (fileStatusLockObject != IndexingFlag.getNonExistentHash()) {
        IndexingFlag.unlockFile(file);
      }
    }
  }

  /**
   * Applying modifications to the index.
   *
   * @param indexerFilter executor index of appliers and removers to proceed, or {@code -1} if we should proceed all of them.
   * @see IndexUpdateWriter#getExecutorIndex(IndexId)
   */
  private void applyModifications(@NotNull VirtualFile file,
                                  int indexerFilter,
                                  @Nullable AtomicInteger syncCounter,
                                  @Nullable Runnable finishCallback,
                                  @NotNull String debugThreadString) {
    var startTime = System.nanoTime();
    boolean allModificationsSuccessful = true;
    try {
      for (SingleIndexValueApplier<?> applier : appliers) {
        if (indexerFilter < 0 || indexerFilter == getExecutorIndex(applier.indexId)) {
          boolean applied = applier.apply();
          if (!applied) {
            allModificationsSuccessful = false;
            VfsEventsMerger.tryLog("NOT_APPLIED", file, () -> applier.toString());
          }
        }
      }

      for (SingleIndexValueRemover remover : removers) {
        if (indexerFilter < 0 || indexerFilter == getExecutorIndex(remover.indexId)) {
          boolean removed = remover.remove();
          if (!removed) {
            allModificationsSuccessful = false;
            VfsEventsMerger.tryLog("NOT_REMOVED", file, () -> remover.toString());
          }
        }
      }
    }
    catch (ProcessCanceledException pce) {
      allModificationsSuccessful = false;
      if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
        Logger.getInstance(FileIndexesValuesApplier.class)
          .infoWithDebug("applyModifications interrupted,fileId=" + fileId + "," + pce, new RuntimeException(pce));
      }
      throw pce;
    }
    catch (Throwable t) {
      allModificationsSuccessful = false;
      Logger.getInstance(FileIndexesValuesApplier.class)
        .warn("applyModifications interrupted,fileId=" + fileId + "," + t, FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES ? t : null);
      throw t;
    }
    finally {
      var lastOrOnlyInvocationForFile = syncCounter == null || syncCounter.decrementAndGet() == 0;
      String debugString = "applied: appliers=" + appliers + " removers=" + removers + "," + debugThreadString;
      if (lastOrOnlyInvocationForFile) {
        doPostModificationJob(file, allModificationsSuccessful, debugString);
      }
      else {
        VfsEventsMerger.tryLog("HAS_MORE_MODIFICATIONS", file, () -> debugString);
      }
      separateApplicationTimeNanos.addAndGet(System.nanoTime() - startTime);
      if (lastOrOnlyInvocationForFile && finishCallback != null) {
        finishCallback.run();
      }
    }
  }

  public long getSeparateApplicationTimeNanos() {
    var result = separateApplicationTimeNanos.get();
    if (result == -1) throw new IllegalStateException("Index values were not applied");
    return result;
  }
}
