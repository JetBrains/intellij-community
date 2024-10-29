// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.dependencies.FileIndexingStamp;
import com.intellij.util.indexing.diagnostic.FileIndexingStatistics;
import com.intellij.util.indexing.diagnostic.IndexesEvaluated;
import com.intellij.util.indexing.events.VfsEventsMerger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Carries the results of indexing given file [file] by all applicable indexers.
 * The actual results of indexing are in [appliers] and [removers] -- to be applied to the index storage
 */
@ApiStatus.Internal
public final class FileIndexingResult {
  /**
   * How to apply changes to the indexes.
   * Currently, there are either apply-in-parallel (or erase if needed), or apply-in-same-thread outside read lock.
   */
  public enum ApplicationMode {
    SameThreadOutsideReadLock, AnotherThread
  }

  private final @NotNull FileBasedIndexImpl indexImpl;

  private final @NotNull VirtualFile file;
  private final int fileId;

  private final @NotNull ApplicationMode applicationMode;

  private final @NotNull FileIndexingStamp indexingStamp;
  private final @NotNull List<SingleIndexValueApplier<?>> appliers;
  private final @NotNull List<SingleIndexValueRemover> removers;
  private final boolean removeDataFromIndicesForFile;
  private final boolean shouldMarkFileAsIndexed;
  private final long fileStatusLockObject;

  private final @NotNull FileIndexingStatistics stats;
  private final AtomicLong separateApplicationTimeNanos = new AtomicLong(0);


  FileIndexingResult(@NotNull FileBasedIndexImpl index,
                     int fileId,
                     @NotNull VirtualFile file,
                     @NotNull FileIndexingStamp indexingStamp,
                     @NotNull List<SingleIndexValueApplier<?>> appliers,
                     @NotNull List<SingleIndexValueRemover> removers,
                     boolean removeDataFromIndicesForFile,
                     boolean shouldMarkFileAsIndexed,
                     @NotNull ApplicationMode applicationMode,
                     @NotNull FileType fileType,
                     boolean logEmptyProvidedIndexes) {
    indexImpl = index;

    this.file = file;
    this.fileId = fileId;
    this.appliers = appliers;
    this.removers = removers;

    this.indexingStamp = indexingStamp;

    this.removeDataFromIndicesForFile = removeDataFromIndicesForFile;
    this.shouldMarkFileAsIndexed = shouldMarkFileAsIndexed;
    this.fileStatusLockObject = shouldMarkFileAsIndexed && !IndexingFlag.isIndexedFlagDisabled()
                                ? IndexingFlag.getOrCreateHash(file)
                                : IndexingFlag.getNonExistentHash();

    this.applicationMode = applicationMode;

    stats = createStats(file, appliers, removers, fileType, logEmptyProvidedIndexes);
  }


  public @NotNull ApplicationMode getApplicationMode() {
    return applicationMode;
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

  public void markFileProcessed(boolean allModificationsSuccessful,
                                @NotNull Supplier<String> debugString) {
    if (allModificationsSuccessful) {
      VfsEventsMerger.tryLog("INDEX_UPDATED", file, debugString);
      indexImpl.getFilesToUpdateCollector().removeFileIdFromFilesScheduledForUpdate(fileId);

      if (shouldMarkFileAsIndexed) {
        IndexingFlag.setIndexedIfFileWithSameLock(file, fileStatusLockObject, indexingStamp);
      }
      else if (fileStatusLockObject != IndexingFlag.getNonExistentHash()) {
        IndexingFlag.unlockFile(file);
      }
    }
    else {
      VfsEventsMerger.tryLog("INDEX_PARTIAL_UPDATE", file, debugString);
      if (fileStatusLockObject != IndexingFlag.getNonExistentHash()) {
        IndexingFlag.unlockFile(file);
      }
    }
  }


  public long applicationTimeNanos() {
    return separateApplicationTimeNanos.get();
  }

  public void addApplicationTimeNanos(long nanos) {
    separateApplicationTimeNanos.addAndGet(nanos);
  }

  public VirtualFile file() {
    return file;
  }

  public int fileId() {
    return fileId;
  }

  public @NotNull FileIndexingStatistics statistics() {
    return stats;
  }

  public @NotNull List<SingleIndexValueApplier<?>> appliers() {
    return appliers;
  }

  public @NotNull List<SingleIndexValueRemover> removers() {
    return removers;
  }

  public @NotNull FileBasedIndexImpl indexImpl() {
    return indexImpl;
  }

  public boolean removeDataFromIndicesForFile() {
    return removeDataFromIndicesForFile;
  }
}
