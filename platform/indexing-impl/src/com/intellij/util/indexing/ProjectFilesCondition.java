// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.events.FileIndexingRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProjectFilesCondition {
  private static final int MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT = 2;
  private final VirtualFile myRestrictedTo;
  private final GlobalSearchScope myFilter;
  private int myFilesFromOtherProjects;
  private final IdFilter myIndexableFilesFilter;

  public ProjectFilesCondition(IdFilter indexableFilesFilter,
                        GlobalSearchScope filter,
                        VirtualFile restrictedTo,
                        boolean includeFilesFromOtherProjects) {
    myRestrictedTo = restrictedTo;
    myFilter = filter;
    myIndexableFilesFilter = indexableFilesFilter;
    if (!includeFilesFromOtherProjects) {
      myFilesFromOtherProjects = MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT;
    }
  }

  private boolean acceptsFileAndId(VirtualFile file, int fileId) {
    if (myIndexableFilesFilter != null && !myIndexableFilesFilter.containsFileId(fileId)) {
      if (myFilesFromOtherProjects >= MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT) return false;
      ++myFilesFromOtherProjects;
      return true;
    }

    if (FileBasedIndexEx.belongsToScope(file, myRestrictedTo, myFilter)) return true;

    if (myFilesFromOtherProjects < MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT) {
      ++myFilesFromOtherProjects;
      return true;
    }
    return false;
  }

  public boolean acceptsFile(@Nullable VirtualFile file) {
    if (file == null) {
      return false;
    }
    else if (file instanceof VirtualFileWithId fileWithId) {
      return acceptsFileAndId(file, fileWithId.getId());
    }
    else {
      return acceptsFileAndId(file, 0);
    }
  }

  public boolean acceptsRequest(@NotNull FileIndexingRequest request) {
    if (request.isDeleteRequest()) {
      return true;
    }

    return acceptsFileAndId(request.getFile(), request.getFileId());
  }
}
