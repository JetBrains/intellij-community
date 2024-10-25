// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface EntityIndexingService {

  @NotNull
  static EntityIndexingService getInstance() {
    return ApplicationManager.getApplication().getService(EntityIndexingService.class);
  }

  @NotNull
  BuildableRootsChangeRescanningInfo createBuildableInfoBuilder();

  boolean isFromWorkspaceOnly(@NotNull List<? extends RootsChangeRescanningInfo> indexingInfos);
}
