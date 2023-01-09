// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.Processor;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl;
import com.intellij.util.indexing.diagnostic.ScanningStatistics;
import com.intellij.util.indexing.diagnostic.ScanningType;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class FileBasedIndexProjectHandler {
  private static final Logger LOG = Logger.getInstance(FileBasedIndexProjectHandler.class);

  @ApiStatus.Internal
  public static final int ourMinFilesToStartDumbMode = Registry.intValue("ide.dumb.mode.minFilesToStart", 20);
  private static final int ourMinFilesSizeToStartDumbMode = Registry.intValue("ide.dumb.mode.minFilesSizeToStart", 1048576);

  public static void scheduleReindexingInDumbMode(@NotNull Project project) {
    final FileBasedIndex i = FileBasedIndex.getInstance();
    if (i instanceof FileBasedIndexImpl &&
        IndexInfrastructure.hasIndices() &&
        !project.isDisposed() &&
        mightHaveManyChangedFilesInProject(project)) {
      new ProjectChangedFilesScanner(project).queue(project);
    }
  }

  @ApiStatus.Internal
  public static boolean mightHaveManyChangedFilesInProject(Project project) {
    FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    if (!(fileBasedIndex instanceof FileBasedIndexImpl)) return false;
    long start = System.currentTimeMillis();
    return !((FileBasedIndexImpl)fileBasedIndex).processChangedFiles(project, new Processor<>() {
      int filesInProjectToBeIndexed;
      long sizeOfFilesToBeIndexed;

      @Override
      public boolean process(VirtualFile file) {
        ++filesInProjectToBeIndexed;
        if (file.isValid() && !file.isDirectory()) sizeOfFilesToBeIndexed += file.getLength();
        return filesInProjectToBeIndexed < ourMinFilesToStartDumbMode &&
               sizeOfFilesToBeIndexed < ourMinFilesSizeToStartDumbMode &&
               System.currentTimeMillis() < start + 100;
      }
    });
  }

  private static class ProjectChangedFilesScanner extends DumbModeTask {
    private @NotNull final Project myProject;

    private ProjectChangedFilesScanner(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(false);
      indicator.setText(IndexingBundle.message("progress.indexing.updating"));

      ProjectIndexingHistoryImpl projectIndexingHistory = new ProjectIndexingHistoryImpl(myProject,
                                                                                         "On refresh of files in " + myProject.getName(),
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
        new UnindexedFilesIndexer(myProject, files).indexFiles(projectIndexingHistory, indicator);
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
        IndexableFilesIterator provider = new IndexableFilesIteratorForRefreshedFiles(myProject,
                                                                                      fileSetName,
                                                                                      IndexingBundle.message("progress.indexing.updating"),
                                                                                      IndexingBundle.message("progress.indexing.scanning"));
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

  private static class IndexableFilesIteratorForRefreshedFiles implements IndexableFilesIterator {
    private final @NotNull Project project;
    private final @NonNls String debugName;
    private final @NlsContexts.ProgressText String indexingProgressText;
    private final @NlsContexts.ProgressText String rootsScanningProgressText;

    private IndexableFilesIteratorForRefreshedFiles(@NotNull Project project, @NonNls String debugName,
                                                    @NlsContexts.ProgressText String indexingProgressText,
                                                    @NlsContexts.ProgressText String rootsScanningProgressText) {
      this.project = project;
      this.debugName = debugName;
      this.indexingProgressText = indexingProgressText;
      this.rootsScanningProgressText = rootsScanningProgressText;
    }

    @Override
    public String getDebugName() {
      return debugName;
    }

    @Override
    public String getIndexingProgressText() {
      return indexingProgressText;
    }

    @Override
    public String getRootsScanningProgressText() {
      return rootsScanningProgressText;
    }

    @Override
    public @NotNull IndexableSetOrigin getOrigin() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean iterateFiles(@NotNull Project project, @NotNull ContentIterator fileIterator, @NotNull VirtualFileFilter fileFilter) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Set<String> getRootUrls(@NotNull Project project) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
      // We need equals because otherwise UnindexedFilesIndexer will not be able to merge files (it merges files per provider, not globally,
      // i.e. the same file assigned to different providers may be indexed twice)
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      IndexableFilesIteratorForRefreshedFiles files = (IndexableFilesIteratorForRefreshedFiles)o;
      // We don't expect that IndexableFilesIterator for different projects be compared, so we could return true here.
      // But it's better to be on safe side, that's why we compare projects.
      return Objects.equals(project, files.project);
    }

    @Override
    public int hashCode() {
      return project.hashCode();
    }
  }
}
