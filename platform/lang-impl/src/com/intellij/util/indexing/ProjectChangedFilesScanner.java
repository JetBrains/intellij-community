// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.diagnostic.ChangedFilesDuringIndexingStatistics;
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistoryImpl;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl;
import com.intellij.util.indexing.diagnostic.ScanningStatistics;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

class ProjectChangedFilesScanner {
  private static final Logger LOG = Logger.getInstance(ProjectChangedFilesScanner.class);
  private @NotNull final Project myProject;

  ProjectChangedFilesScanner(@NotNull Project project) {
    myProject = project;
  }

  public Collection<VirtualFile> scan(ProjectIndexingHistoryImpl projectIndexingHistory,
                                      @NotNull ProjectDumbIndexingHistoryImpl projectDumbIndexingHistory,
                                      String fileSetName) {
    long refreshedFilesCalcDuration = System.nanoTime();
    Collection<VirtualFile> files = Collections.emptyList();
    try {
      FileBasedIndexImpl fileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
      files = fileBasedIndex.getFilesToUpdate(myProject);
      return files;
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

      projectDumbIndexingHistory.setChangedFilesDuringIndexingStatistics(
        new ChangedFilesDuringIndexingStatistics(files.size(), refreshedFilesCalcDuration));

      LOG.info("Retrieving changed during indexing files of " + myProject.getName() + " : " + files.size() + " to update, " +
               "calculated in " + TimeUnit.NANOSECONDS.toMillis(refreshedFilesCalcDuration) + "ms");
    }
  }
}
