// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.diagnostic.IndexingJobStatistics;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory;
import com.intellij.util.indexing.diagnostic.ScanningStatistics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;

public final class FileBasedIndexProjectHandler {
  private static final Logger LOG = Logger.getInstance(FileBasedIndexProjectHandler.class);

  @ApiStatus.Internal
  public static final int ourMinFilesToStartDumbMode = Registry.intValue("ide.dumb.mode.minFilesToStart", 20);
  private static final int ourMinFilesSizeToStartDumbMode = Registry.intValue("ide.dumb.mode.minFilesSizeToStart", 1048576);

  /**
   * @deprecated Use {@see scheduleReindexingInDumbMode()} instead.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @Nullable
  public static DumbModeTask createChangedFilesIndexingTask(@NotNull Project project) {
    final FileBasedIndex i = FileBasedIndex.getInstance();
    if (!(i instanceof FileBasedIndexImpl) || !IndexInfrastructure.hasIndices()) {
      return null;
    }
    if (project.isDisposed()) return null;

    if (!mightHaveManyChangedFilesInProject(project)) {
      return null;
    }

    return new ProjectChangedFilesIndexingTask(project);
  }

  public static void scheduleReindexingInDumbMode(@NotNull Project project) {
    DumbModeTask task = createChangedFilesIndexingTask(project);
    if (task != null) {
      DumbService.getInstance(project).queueTask(task);
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

  private static class ProjectChangedFilesIndexingTask extends DumbModeTask {
    private @NotNull final Project myProject;

    private ProjectChangedFilesIndexingTask(@NotNull Project project) {
      super(project);
      myProject = project;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(false);
      indicator.setText(IndexingBundle.message("progress.indexing.updating"));

      long start = System.currentTimeMillis();
      FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
      Collection<VirtualFile> files = fileBasedIndex.getFilesToUpdate(myProject);
      long calcDuration = System.currentTimeMillis() - start;

      LOG.info("Reindexing refreshed files: " + files.size() + " to update, calculated in " + calcDuration + "ms");
      if (!files.isEmpty()) {
        PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
        indexChangedFiles(files, indicator, fileBasedIndex, myProject);
        snapshot.logResponsivenessSinceCreation("Reindexing refreshed files");
      }
    }

    private static void indexChangedFiles(@NotNull Collection<VirtualFile> files,
                                          @NotNull ProgressIndicator indicator,
                                          @NotNull FileBasedIndexImpl index,
                                          @NotNull Project project) {
      ProjectIndexingHistory projectIndexingHistory = new ProjectIndexingHistory(project);
      IndexDiagnosticDumper.getInstance().onIndexingStarted(projectIndexingHistory);
      ((FileBasedIndexImpl)FileBasedIndex.getInstance()).fireUpdateStarted(project);

      try {
        int numberOfIndexingThreads = UnindexedFilesUpdater.getNumberOfIndexingThreads();
        LOG.info("Using " + numberOfIndexingThreads + " " + StringUtil.pluralize("thread", numberOfIndexingThreads) + " for indexing");
        IndexUpdateRunner indexUpdateRunner = new IndexUpdateRunner(
          index, UnindexedFilesUpdater.GLOBAL_INDEXING_EXECUTOR, numberOfIndexingThreads
        );
        IndexingJobStatistics statistics;
        IndexUpdateRunner.IndexingInterruptedException interruptedException = null;
        Instant indexingStart = Instant.now();
        String fileSetName = "Refreshed files";
        try {
          statistics = indexUpdateRunner.indexFiles(project, fileSetName, files, indicator);
        }
        catch (IndexUpdateRunner.IndexingInterruptedException e) {
          projectIndexingHistory.getTimes().setWasInterrupted(true);
          statistics = e.myStatistics;
          interruptedException = e;
        }
        finally {
          Instant now = Instant.now();
          projectIndexingHistory.getTimes().setIndexingDuration(Duration.between(indexingStart, now));
          projectIndexingHistory.getTimes().setUpdatingEnd(ZonedDateTime.now(ZoneOffset.UTC));
          projectIndexingHistory.getTimes().setTotalUpdatingTime(System.nanoTime() - projectIndexingHistory.getTimes().getTotalUpdatingTime());
        }
        ScanningStatistics scanningStatistics = new ScanningStatistics(fileSetName);
        scanningStatistics.setNumberOfScannedFiles(files.size());
        scanningStatistics.setNumberOfFilesForIndexing(files.size());
        projectIndexingHistory.addScanningStatistics(scanningStatistics);
        projectIndexingHistory.addProviderStatistics(statistics);

        if (interruptedException != null) {
          ExceptionUtil.rethrow(interruptedException.getCause());
        }
      }
      finally {
        IndexDiagnosticDumper.getInstance().onIndexingFinished(projectIndexingHistory);
        ((FileBasedIndexImpl)FileBasedIndex.getInstance()).fireUpdateFinished(project);
      }
    }

    @Override
    public String toString() {
      StringBuilder sampleOfChangedFilePathsToBeIndexed = new StringBuilder();

      ((FileBasedIndexImpl)FileBasedIndex.getInstance()).processChangedFiles(myProject, new Processor<>() {
        int filesInProjectToBeIndexed;
        final String projectBasePath = myProject.getBasePath();

        @Override
        public boolean process(VirtualFile file) {
          if (filesInProjectToBeIndexed != 0) sampleOfChangedFilePathsToBeIndexed.append(", ");

          String filePath = file.getPath();
          String loggedPath = projectBasePath != null ? FileUtil.getRelativePath(projectBasePath, filePath, '/') : null;
          loggedPath = loggedPath == null ? filePath : "%project_path%/" + loggedPath;
          sampleOfChangedFilePathsToBeIndexed.append(loggedPath);

          return ++filesInProjectToBeIndexed < ourMinFilesToStartDumbMode;
        }
      });
      return super.toString() + " [" + myProject + ", " + sampleOfChangedFilePathsToBeIndexed + "]";
    }
  }
}
