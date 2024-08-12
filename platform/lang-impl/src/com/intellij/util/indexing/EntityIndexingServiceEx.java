// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.jps.entities.LibraryId;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface EntityIndexingServiceEx extends EntityIndexingService {

  static @NotNull EntityIndexingServiceEx getInstanceEx() {
    return (EntityIndexingServiceEx)EntityIndexingService.getInstance();
  }

  @NotNull
  RootsChangeRescanningInfo createWorkspaceChangedEventInfo(@NotNull List<EntityChange<?>> changes);

  @NotNull
  RootsChangeRescanningInfo createWorkspaceEntitiesRootsChangedInfo(@NotNull List<EntityPointer<WorkspaceEntity>> references);

  boolean shouldCauseRescan(@Nullable WorkspaceEntity oldEntity, @Nullable WorkspaceEntity newEntity, @NotNull Project project);

  @RequiresBackgroundThread
  @NotNull
  Collection<IndexableFilesIterator> createIteratorsForOrigins(@NotNull Project project,
                                                               @NotNull EntityStorage entityStorage,
                                                               @NotNull Collection<EntityPointer<?>> entityPointers,
                                                               @NotNull Collection<Sdk> sdks,
                                                               @NotNull Collection<LibraryId> libraryIds,
                                                               @NotNull Collection<VirtualFile> filesFromAdditionalLibraryRootsProviders,
                                                               @NotNull Collection<VirtualFile> filesFromIndexableSetContributors);
}