// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.Processor;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

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

  static class IndexableFilesIteratorForRefreshedFiles implements IndexableFilesIterator {
    private final @NotNull Project project;
    private final @NonNls String debugName;
    private final @NlsContexts.ProgressText String indexingProgressText;
    private final @NlsContexts.ProgressText String rootsScanningProgressText;

    IndexableFilesIteratorForRefreshedFiles(@NotNull Project project, @NonNls String debugName,
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
