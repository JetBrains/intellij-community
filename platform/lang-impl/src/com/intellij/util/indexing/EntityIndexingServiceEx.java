// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface EntityIndexingServiceEx extends EntityIndexingService {

  @NotNull
  static EntityIndexingServiceEx getInstanceEx() {
    return (EntityIndexingServiceEx)EntityIndexingService.getInstance();
  }

  @NotNull
  RootsChangeRescanningInfo createWorkspaceChangedEventInfo(@NotNull List<EntityChange<?>> changes);

  @NotNull
  RootsChangeRescanningInfo createWorkspaceEntitiesRootsChangedInfo(@NotNull List<EntityReference<WorkspaceEntity>> references);

  boolean shouldCauseRescan(@NotNull WorkspaceEntity entity, @NotNull Project project);

  @RequiresBackgroundThread
  @NotNull
  Collection<IndexableFilesIterator> createIteratorsForOrigins(@NotNull Project project,
                                                               @NotNull EntityStorage entityStorage,
                                                               @NotNull Collection<EntityReference<?>> entityReferences,
                                                               @NotNull Collection<Sdk> sdks,
                                                               @NotNull Collection<LibraryId> libraryIds,
                                                               @NotNull Collection<VirtualFile> filesFromAdditionalLibraryRootsProviders,
                                                               @NotNull Collection<VirtualFile> filesFromIndexableSetContributors);
}