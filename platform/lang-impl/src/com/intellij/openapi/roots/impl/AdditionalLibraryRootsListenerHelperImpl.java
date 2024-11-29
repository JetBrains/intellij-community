// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsListenerHelper;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.UnindexedFilesScanner;
import com.intellij.util.indexing.roots.AdditionalLibraryIndexableAddedFilesIterator;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class AdditionalLibraryRootsListenerHelperImpl implements AdditionalLibraryRootsListenerHelper {
  @Override
  public void handleAdditionalLibraryRootsChanged(@NotNull Project project,
                                                  @Nls @Nullable String presentableLibraryName,
                                                  @NotNull Collection<? extends VirtualFile> oldRoots,
                                                  @NotNull Collection<? extends VirtualFile> newRoots,
                                                  @NotNull String libraryNameForDebug) {
    DirectoryIndex directoryIndex = DirectoryIndex.getInstance(project);
    if (directoryIndex instanceof DirectoryIndexImpl) {
      ((DirectoryIndexImpl)directoryIndex).reset();
    }
    ((WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project)).getIndexData().resetCustomContributors();
    additionalLibraryRootsChanged(project, presentableLibraryName, oldRoots, newRoots, libraryNameForDebug);
  }


  private static void additionalLibraryRootsChanged(@NotNull Project project,
                                                    @Nls @Nullable String presentableLibraryName,
                                                    @NotNull Collection<? extends VirtualFile> oldRoots,
                                                    @NotNull Collection<? extends VirtualFile> newRoots,
                                                    @NotNull String libraryNameForDebug) {
    if (!(FileBasedIndex.getInstance() instanceof FileBasedIndexImpl)) {
      return;
    }
    List<VirtualFile> rootsToIndex = new ArrayList<>(newRoots.size());
    for (VirtualFile root : newRoots) {
      boolean shouldIndex = true;
      for (VirtualFile oldRoot : oldRoots) {
        if (VfsUtilCore.isAncestor(oldRoot, root, false)) {
          shouldIndex = false;
          break;
        }
      }
      if (shouldIndex) {
        rootsToIndex.add(root);
      }
    }

    if (rootsToIndex.isEmpty()) return;

    List<IndexableFilesIterator> indexableFilesIterators =
      Collections.singletonList(new AdditionalLibraryIndexableAddedFilesIterator(presentableLibraryName, rootsToIndex, libraryNameForDebug));

    new UnindexedFilesScanner(project, indexableFilesIterators, "On updated roots of library '" + presentableLibraryName + "'").
      queue();
  }
}
