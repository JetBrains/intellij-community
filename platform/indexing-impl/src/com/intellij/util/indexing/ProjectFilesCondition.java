// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.events.FileIndexingRequest;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
final class ProjectFilesCondition implements Condition<FileIndexingRequest> {
  private static final int MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT = 2;
  private final VirtualFile myRestrictedTo;
  private final GlobalSearchScope myFilter;
  private int myFilesFromOtherProjects;
  private final IdFilter myIndexableFilesFilter;

  ProjectFilesCondition(IdFilter indexableFilesFilter,
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

  @Override
  public boolean value(FileIndexingRequest request) {
    if (request.isDeleteRequest()) {
      return true;
    }

    VirtualFile file = request.getFile();
    int fileId = request.getFileId();
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
}
