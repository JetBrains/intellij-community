// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.indexing.dependencies.IndexingRequestToken;
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class FileBasedIndexProjectHandler {
  @ApiStatus.Internal
  public static final int ourMinFilesToStartDumbMode = Registry.intValue("ide.dumb.mode.minFilesToStart", 20);
  private static final int ourMinFilesSizeToStartDumbMode = Registry.intValue("ide.dumb.mode.minFilesSizeToStart", 1048576);

  public static void scheduleReindexingInDumbMode(@NotNull Project project) {
    final FileBasedIndex i = FileBasedIndex.getInstance();
    if (i instanceof FileBasedIndexImpl &&
        IndexInfrastructure.hasIndices() &&
        !project.isDisposed() &&
        mightHaveManyChangedFilesInProject(project)) {

      String indexingReason = "On refresh of files in " + project.getName();
      IndexingRequestToken rescan = project.getService(ProjectIndexingDependenciesService.class).invalidateAllStamps();
      new UnindexedFilesIndexer(project, indexingReason, rescan).queue(project);
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
}
