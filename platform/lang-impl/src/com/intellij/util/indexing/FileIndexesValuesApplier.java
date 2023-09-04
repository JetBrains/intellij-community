// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.diagnostic.FileIndexingStatistics;
import com.intellij.util.indexing.events.VfsEventsMerger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public final class FileIndexesValuesApplier {
  private final FileBasedIndexImpl myIndex;
  private final int fileId;
  private final @NotNull List<? extends SingleIndexValueApplier<?>> appliers;
  private final @NotNull List<SingleIndexValueRemover> removers;
  private final boolean removeDataFromIndicesForFile;
  @NotNull
  public final FileIndexingStatistics stats;
  public final boolean isWriteValuesSeparately;
  private long separateApplicationTimeNanos = -1;

  FileIndexesValuesApplier(FileBasedIndexImpl index, int fileId,
                           @NotNull VirtualFile file,
                           @NotNull List<? extends SingleIndexValueApplier<?>> appliers,
                           @NotNull List<SingleIndexValueRemover> removers,
                           boolean removeDataFromIndicesForFile,
                           boolean writeValuesSeparately,
                           @NotNull FileType fileType,
                           boolean logEmptyProvidedIndexes) {
    myIndex = index;
    this.fileId = fileId;
    this.appliers = appliers;
    this.removers = removers;
    this.removeDataFromIndicesForFile = removeDataFromIndicesForFile;
    isWriteValuesSeparately = writeValuesSeparately;
    stats = createStats(file, appliers, removers, fileType, logEmptyProvidedIndexes);
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
        if (myIndex.doTraceSharedIndexUpdates()) {
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
    return new FileIndexingStatistics(fileType,
                                      indexesProvidedByExtensions,
                                      !indexesProvidedByExtensions.isEmpty() && wasFullyIndexedByInfrastructureExtension,
                                      perIndexerEvaluatingIndexValueAppliersTimes,
                                      perIndexerEvaluatingIndexValueRemoversTimes);
  }

  void applyImmediately(@NotNull VirtualFile file, boolean isValid) {
    if (isWriteValuesSeparately) {
      return;
    }
    if (removeDataFromIndicesForFile) {
      myIndex.removeDataFromIndicesForFile(fileId, file, "invalid_or_large_file");
    }

    VfsEventsMerger.tryLog("INDEX_UPDATED", file,
                           () -> " updated_indexes=" + stats.getPerIndexerEvaluateIndexValueTimes().keySet() +
                                 " deleted_indexes=" + stats.getPerIndexerEvaluatingIndexValueRemoversTimes().keySet() +
                                 " valid=" + isValid);
    myIndex.getChangedFilesCollector().removeFileIdFromFilesScheduledForUpdate(fileId);
  }

  public void apply(@NotNull VirtualFile file) {
    if (!isWriteValuesSeparately) {
      return;
    }
    long applicationStart = System.nanoTime();
    try {
      if (removeDataFromIndicesForFile) {
        myIndex.removeDataFromIndicesForFile(fileId, file, "invalid_or_large_file");
      }
      if (!appliers.isEmpty() || !removers.isEmpty()) {
        for (SingleIndexValueApplier<?> applier : appliers) {
          applier.apply();
        }

        for (SingleIndexValueRemover remover : removers) {
          remover.remove();
        }
      }
      VfsEventsMerger.tryLog("INDEX_UPDATED", file,
                             () -> " updated_indexes=" + stats.getPerIndexerEvaluateIndexValueTimes().keySet() +
                                   " deleted_indexes=" + stats.getPerIndexerEvaluatingIndexValueRemoversTimes().keySet());
      myIndex.getChangedFilesCollector().removeFileIdFromFilesScheduledForUpdate(fileId);
    }
    finally {
      separateApplicationTimeNanos = System.nanoTime() - applicationStart;
    }
  }

  public long getSeparateApplicationTimeNanos() {
    if (!isWriteValuesSeparately) return 0;
    if (separateApplicationTimeNanos == -1) throw new IllegalStateException("Index values were not applied");
    return separateApplicationTimeNanos;
  }
}
