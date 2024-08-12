// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.platform.workspace.storage.WorkspaceEntity;
import org.jetbrains.annotations.NotNull;

public abstract class BuildableRootsChangeRescanningInfoEx extends BuildableRootsChangeRescanningInfo {
  public abstract @NotNull BuildableRootsChangeRescanningInfoEx addWorkspaceEntity(@NotNull WorkspaceEntity entity);
}
