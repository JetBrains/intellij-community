// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl;
import com.intellij.util.indexing.diagnostic.ScanningStatistics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
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
      new ProjectChangedFilesIndexingTask(project).queue(project);
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
      myProject = project;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(false);
      indicator.setText(IndexingBundle.message("progress.indexing.updating"));

      long refreshedFilesCalcDuration = System.nanoTime();
      FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
      Collection<VirtualFile> files = fileBasedIndex.getFilesToUpdate(myProject);
      refreshedFilesCalcDuration = System.nanoTime() - refreshedFilesCalcDuration;

      LOG.info("Reindexing refreshed files of " + myProject.getName() + " : " + files.size() + " to update, calculated in " + TimeUnit.NANOSECONDS.toMillis(refreshedFilesCalcDuration) + "ms");
      if (!files.isEmpty()) {
        PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
        indexChangedFiles(files, indicator, fileBasedIndex, myProject, refreshedFilesCalcDuration);
        snapshot.logResponsivenessSinceCreation("Reindexing refreshed files of " + myProject.getName());
      }
    }

    private static void indexChangedFiles(@NotNull Collection<VirtualFile> files,
                                          @NotNull ProgressIndicator indicator,
                                          @NotNull FileBasedIndexImpl index,
                                          @NotNull Project project,
                                          long refreshedFilesCalcDuration) {
      ProjectIndexingHistoryImpl projectIndexingHistory =
        new ProjectIndexingHistoryImpl(project, "On refresh of " + files.size() + " files", false);
      IndexDiagnosticDumper.getInstance().onIndexingStarted(projectIndexingHistory);
      ((FileBasedIndexImpl)FileBasedIndex.getInstance()).fireUpdateStarted(project);

      try {
        int numberOfIndexingThreads = UnindexedFilesUpdater.getNumberOfIndexingThreads();
        LOG.info("Using " + numberOfIndexingThreads + " " + StringUtil.pluralize("thread", numberOfIndexingThreads) + " for indexing");
        IndexUpdateRunner indexUpdateRunner = new IndexUpdateRunner(
          index, UnindexedFilesUpdater.GLOBAL_INDEXING_EXECUTOR, numberOfIndexingThreads
        );
        IndexUpdateRunner.IndexingInterruptedException interruptedException = null;
        projectIndexingHistory.startStage(ProjectIndexingHistoryImpl.Stage.Indexing);
        String fileSetName = "Refreshed files";
        IndexUpdateRunner.FileSet fileSet = new IndexUpdateRunner.FileSet(project, fileSetName, files);
        try {
          indexUpdateRunner.indexFiles(project, Collections.singletonList(fileSet), indicator, projectIndexingHistory);
        }
        catch (IndexUpdateRunner.IndexingInterruptedException e) {
          projectIndexingHistory.setWasInterrupted(true);
          interruptedException = e;
        }
        finally {
          projectIndexingHistory.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing);
          projectIndexingHistory.finishTotalUpdatingTime();
        }
        ScanningStatistics scanningStatistics = new ScanningStatistics(fileSetName);
        scanningStatistics.setNumberOfScannedFiles(files.size());
        scanningStatistics.setNumberOfFilesForIndexing(files.size());
        scanningStatistics.setScanningTime(refreshedFilesCalcDuration);
        scanningStatistics.setNoRootsForRefresh();
        projectIndexingHistory.addScanningStatistics(scanningStatistics);
        projectIndexingHistory.addProviderStatistics(fileSet.statistics);

        if (interruptedException != null) {
          ExceptionUtil.rethrow(interruptedException.getCause());
        }
      }
      finally {
        IndexDiagnosticDumper.getInstance().onIndexingFinished(projectIndexingHistory);
        projectIndexingHistory.setScanFilesDuration(Duration.ofNanos(refreshedFilesCalcDuration));
        ((FileBasedIndexImpl)FileBasedIndex.getInstance()).fireUpdateFinished(project);
      }
    }

    @Override
    public @Nullable DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
      if (taskFromQueue instanceof ProjectChangedFilesIndexingTask &&
          ((ProjectChangedFilesIndexingTask)taskFromQueue).myProject.equals(myProject)) {
        return this;
      }
      return null;
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
