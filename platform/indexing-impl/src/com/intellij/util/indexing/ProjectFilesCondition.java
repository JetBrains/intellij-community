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

  private final IdFilter myIndexableFilesFilter;
  private final GlobalSearchScope myFilter;
  private final VirtualFile myRestrictedTo;

  private int myFilesFromOtherProjects;

  //indexableFilesFilter = (ProjectIndexableFilesFilter | null)
  public ProjectFilesCondition(@Nullable IdFilter indexableFilesFilter,
                               @Nullable GlobalSearchScope filter,
                               @Nullable VirtualFile restrictedTo,
                               boolean includeFilesFromOtherProjects) {
    myRestrictedTo = restrictedTo;
    myFilter = filter;
    myIndexableFilesFilter = indexableFilesFilter;
    if (!includeFilesFromOtherProjects) {
      myFilesFromOtherProjects = MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT;
    }
  }

  private boolean acceptsFileAndId(@Nullable VirtualFile file, int fileId) {
    if (belongsTo(file, fileId)) {
      return true;
    }

    //even if the file doesn't belong to the project/scope provided -- give it a chance!
    // MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT chances, really:
    if (myFilesFromOtherProjects < MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT) {
      myFilesFromOtherProjects++;
      return true;
    }
    return false;
  }

  private boolean belongsTo(@Nullable VirtualFile file, int fileId) {
    if (myIndexableFilesFilter != null && !myIndexableFilesFilter.containsFileId(fileId)) {
      return false;
    }
    return FileBasedIndexEx.belongsToScope(file, myRestrictedTo, myFilter);
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
