// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.RootsChangeRescanningInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface EntityIndexingServiceEx extends EntityIndexingService {

  static @NotNull EntityIndexingServiceEx getInstanceEx() {
    return (EntityIndexingServiceEx)EntityIndexingService.getInstance();
  }

  @NotNull
  RootsChangeRescanningInfo createWorkspaceChangedEventInfo();

  @NotNull
  RootsChangeRescanningInfo createWorkspaceEntitiesRootsChangedInfo();
}