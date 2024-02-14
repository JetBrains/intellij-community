// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilterHolder;
import org.jetbrains.annotations.NotNull;

public interface FilesFilterScanningHandler {
  void addFileId(@NotNull Project project, int fileId);

  default void scanningCompleted() {

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
}
