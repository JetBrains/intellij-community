// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.workspaceModel.storage.WorkspaceEntity;
import org.jetbrains.annotations.NotNull;

public abstract class BuildableRootsChangeRescanningInfoEx extends BuildableRootsChangeRescanningInfo {
  @NotNull
  public abstract BuildableRootsChangeRescanningInfo addWorkspaceEntity(@NotNull WorkspaceEntity entity);
}
