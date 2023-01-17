// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  List<EntityReference<WorkspaceEntity>> getReferencesToEntitiesWithChangedRoots(@NotNull List<? extends RootsChangeRescanningInfo> infos);
}