// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilterHealthCheck;
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilterHolder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.indexing.UnindexedFilesScannerStartupKt.setProjectFilterIsInvalidated;

@ApiStatus.Internal
public interface FilesFilterScanningHandler {
  void addFileId(@NotNull Project project, int fileId);

  default void scanningCompleted(@NotNull Project project) {
    setProjectFilterIsInvalidated(project, false);
    ProjectIndexableFilesFilterHealthCheck healthCheck = project.isDisposed() ? null : project.getService(ProjectIndexableFilesFilterHealthCheck.class);
    if (healthCheck != null) {
      healthCheck.launchHealthCheck();
    }
  }

  void scanningStarted(@NotNull Project project, boolean update);

  class UpdatingFilesFilterScanningHandler implements FilesFilterScanningHandler {
    private final ProjectIndexableFilesFilterHolder myFilterHolder;

    public UpdatingFilesFilterScanningHandler(@NotNull ProjectIndexableFilesFilterHolder filterHolder) {
      myFilterHolder = filterHolder;
    }

    @Override
    public void addFileId(@NotNull Project project, int fileId) {
      myFilterHolder.addFileId(fileId, project);
    }

    @Override
    public void scanningStarted(@NotNull Project project, boolean isFullUpdate) {
      if (isFullUpdate) {
        myFilterHolder.resetFileIds(project);
      }
    }
  }

  class IdleFilesFilterScanningHandler implements FilesFilterScanningHandler {
    private static final Logger LOG = Logger.getInstance(IdleFilesFilterScanningHandler.class);

    @Override
    public void addFileId(@NotNull Project project, int fileId) {
    }

    @Override
    public void scanningStarted(@NotNull Project project, boolean update) {
      LOG.info("Scanning will happen without filling of project indexable files filter");
    }
  }
}
