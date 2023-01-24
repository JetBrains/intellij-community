// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl;
import com.intellij.util.indexing.diagnostic.ScanningStatistics;
import com.intellij.util.indexing.diagnostic.ScanningType;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class ProjectChangedFilesScanner extends DumbModeTask {
  private static final Logger LOG = Logger.getInstance(ProjectChangedFilesScanner.class);
  private @NotNull final Project myProject;

  ProjectChangedFilesScanner(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void performInDumbMode(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(false);
    indicator.setText(IndexingBundle.message("progress.indexing.updating"));

    String indexingReason = "On refresh of files in " + myProject.getName();
    ProjectIndexingHistoryImpl projectIndexingHistory = new ProjectIndexingHistoryImpl(myProject,
                                                                                       indexingReason,
                                                                                       ScanningType.REFRESH);

    IndexDiagnosticDumper.getInstance().onIndexingStarted(projectIndexingHistory);

    try {
      Map<IndexableFilesIterator, Collection<VirtualFile>> files = scan(projectIndexingHistory);

      if (files.isEmpty()) {
        LOG.info("Finished for " + myProject.getName() + ". No files to index.");
        return;
      }

      // We would like to use UnindexedFilesIndexer.queue (instead of UnindexedFilesIndexer.indexFiles), but this will lead to redundant scanning tasks.
      // Consider the following situation (we use the following notation: [executing] + [queued] + [new tasks] ; comment):
      //
      // [] + [] + [scanning] ; some thread submitted scanning task
      // [] + [scanning] + []
      // [scanning] + [] + []
      // [scanning] + [] + [scanning]  ; some thread submitted scanning task while the other scanning task is in progress. They are not merged in this case
      // [scanning] + [scanning] + []
      // [scanning] + [scanning] + [indexing]    ; first scanning task is about to complete and submitted an indexing task
      // [] + [scanning, indexing] + []          ; first scanning task finished. We denote this state as (A)
      // [scanning] + [indexing] + []
      // [scanning] + [indexing] + [scanning]    ; some thread submitted new scanning task
      // [scanning] + [indexing, scanning] + []
      // [scanning] + [indexing, scanning] + [indexing] ; scanning task submitted an indexing task
      // [scanning] + [scanning, indexing] + []         ; indexing tasks are merged, merged task in inserted at the end of the queue
      // [] + [scanning, indexing] + []                 ; scanning task finished. This is exactly the same state as (A) 5 lines above
      //
      // Fair amount of FileBasedIndexProjectHandler.scheduleReindexingInDumbMode invocations are observed during massive refresh (e.g. branch change).
      // In practice, we observe 2-3 scanning tasks followed by a single indexing task.
      new UnindexedFilesIndexer(myProject, files, indexingReason).indexFiles(projectIndexingHistory, indicator);
    }
    catch (Exception e) {
      projectIndexingHistory.setWasInterrupted(true);
      if (e instanceof ControlFlowException) {
        LOG.info("Cancelled indexing of " + myProject.getName());
      }
      throw e;
    }
    finally {
      projectIndexingHistory.finishTotalUpdatingTime();
      IndexDiagnosticDumper.getInstance().onIndexingFinished(projectIndexingHistory);
    }
  }

  private Map<IndexableFilesIterator, Collection<VirtualFile>> scan(ProjectIndexingHistoryImpl projectIndexingHistory) {
    String fileSetName = "Refreshed files";
    long refreshedFilesCalcDuration = System.nanoTime();
    Collection<VirtualFile> files = Collections.emptyList();
    try {
      FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
      files = fileBasedIndex.getFilesToUpdate(myProject);
      IndexableFilesIterator provider = new FileBasedIndexProjectHandler.IndexableFilesIteratorForRefreshedFiles(myProject,
                                                                                                                 fileSetName,
                                                                                                                 IndexingBundle.message(
                                                                                                                   "progress.indexing.updating"),
                                                                                                                 IndexingBundle.message(
                                                                                                                   "progress.indexing.scanning"));
      return Collections.singletonMap(provider, files);
    }
    finally {
      refreshedFilesCalcDuration = System.nanoTime() - refreshedFilesCalcDuration;
      ScanningStatistics scanningStatistics = new ScanningStatistics(fileSetName);
      scanningStatistics.setNumberOfScannedFiles(files.size());
      scanningStatistics.setNumberOfFilesForIndexing(files.size());
      scanningStatistics.setScanningTime(refreshedFilesCalcDuration);
      scanningStatistics.setNoRootsForRefresh();
      projectIndexingHistory.addScanningStatistics(scanningStatistics);
      projectIndexingHistory.setScanFilesDuration(Duration.ofNanos(refreshedFilesCalcDuration));

      LOG.info("Scanning refreshed files of " + myProject.getName() + " : " + files.size() + " to update, " +
               "calculated in " + TimeUnit.NANOSECONDS.toMillis(refreshedFilesCalcDuration) + "ms");
    }
  }

  @Override
  public @Nullable DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
    if (taskFromQueue instanceof ProjectChangedFilesScanner &&
        ((ProjectChangedFilesScanner)taskFromQueue).myProject.equals(myProject)) {
      return this;
    }
    return null;
  }
}
