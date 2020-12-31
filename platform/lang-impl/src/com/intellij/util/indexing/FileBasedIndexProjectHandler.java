// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.*;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;

public final class FileBasedIndexProjectHandler {
  private static final Logger LOG = Logger.getInstance(FileBasedIndexProjectHandler.class);

  @ApiStatus.Internal
  public static final int ourMinFilesToStartDumbMode = Registry.intValue("ide.dumb.mode.minFilesToStart", 20);
  private static final int ourMinFilesSizeToStartDumbMode = Registry.intValue("ide.dumb.mode.minFilesSizeToStart", 1048576);

  @Nullable
  public static DumbModeTask createChangedFilesIndexingTask(@NotNull Project project) {
    final FileBasedIndex i = FileBasedIndex.getInstance();
    if (!(i instanceof FileBasedIndexImpl) || !IndexInfrastructure.hasIndices()) {
      return null;
    }

    FileBasedIndexImpl index = (FileBasedIndexImpl)i;
    if (!mightHaveManyChangedFilesInProject(project, index)) {
      return null;
    }

    return new ProjectChangedFilesIndexingTask(project, index);
  }

  private static boolean mightHaveManyChangedFilesInProject(Project project, FileBasedIndexImpl index) {
    long start = System.currentTimeMillis();
    return !index.processChangedFiles(project, new Processor<>() {
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
    private final FileBasedIndexImpl myIndex;

    private ProjectChangedFilesIndexingTask(@NotNull Project project, FileBasedIndexImpl index) {
      super(project);
      myProject = project;
      myIndex = index;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(false);
      indicator.setText(IndexingBundle.message("progress.indexing.updating"));

      long start = System.currentTimeMillis();
      Collection<VirtualFile> files = myIndex.getFilesToUpdate(myProject);
      long calcDuration = System.currentTimeMillis() - start;

      LOG.info("Reindexing refreshed files: " + files.size() + " to update, calculated in " + calcDuration + "ms");
      if (!files.isEmpty()) {
        PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
        indexChangedFiles(files, indicator, myIndex, myProject);
        snapshot.logResponsivenessSinceCreation("Reindexing refreshed files");
      }
    }

    private static void indexChangedFiles(@NotNull Collection<VirtualFile> files,
                                          @NotNull ProgressIndicator indicator,
                                          @NotNull FileBasedIndexImpl index,
                                          @NotNull Project project) {
      int numberOfIndexingThreads = UnindexedFilesUpdater.getNumberOfIndexingThreads();
      LOG.info("Using " + numberOfIndexingThreads + " " + StringUtil.pluralize("thread", numberOfIndexingThreads) + " for indexing");
      IndexUpdateRunner indexUpdateRunner = new IndexUpdateRunner(index, UnindexedFilesUpdater.GLOBAL_INDEXING_EXECUTOR, numberOfIndexingThreads);
      IndexingJobStatistics statistics;
      IndexUpdateRunner.IndexingInterruptedException interruptedException = null;
      ProjectIndexingHistory projectIndexingHistory = new ProjectIndexingHistory(project);
      projectIndexingHistory.getTimes().setIndexingStart(Instant.now());
      try {
        statistics = indexUpdateRunner.indexFiles(project, "Refreshed files", files, indicator);
      } catch (IndexUpdateRunner.IndexingInterruptedException e) {
        projectIndexingHistory.getTimes().setWasInterrupted(true);
        statistics = e.myStatistics;
        interruptedException = e;
      } finally {
        projectIndexingHistory.getTimes().setIndexingEnd(Instant.now());
      }
      projectIndexingHistory.addProviderStatistics(statistics);
      IndexDiagnosticDumper.INSTANCE.dumpProjectIndexingHistoryIfNecessary(projectIndexingHistory);
      if (interruptedException != null) {
        ExceptionUtil.rethrow(interruptedException.getCause());
      }
    }

    @Override
    public String toString() {
      StringBuilder sampleOfChangedFilePathsToBeIndexed = new StringBuilder();

      myIndex.processChangedFiles(myProject, new Processor<VirtualFile>() {
        int filesInProjectToBeIndexed;
        final String projectBasePath = myProject.getBasePath();

        @Override
        public boolean process(VirtualFile file) {
          if (filesInProjectToBeIndexed != 0) sampleOfChangedFilePathsToBeIndexed.append(", ");

          String filePath = file.getPath();
          String loggedPath = projectBasePath != null ? FileUtil.getRelativePath(projectBasePath, filePath, '/') : null;
          if (loggedPath == null) loggedPath = filePath;
          else loggedPath = "%project_path%/" + loggedPath;
          sampleOfChangedFilePathsToBeIndexed.append(loggedPath);

          return ++filesInProjectToBeIndexed < ourMinFilesToStartDumbMode;
        }
      });
      return super.toString() + " [" + myProject + ", " + sampleOfChangedFilePathsToBeIndexed + "]";
    }
  }
}
