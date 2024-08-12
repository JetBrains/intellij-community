// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch.workspace;

import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar;
import org.jetbrains.annotations.NotNull;

public class ScratchRootsEntityWorkspaceFileIndexContributor implements WorkspaceFileIndexContributor<ScratchRootsEntity> {
  @Override
  public @NotNull Class<ScratchRootsEntity> getEntityClass() {
    return ScratchRootsEntity.class;
  }

  @Override
  public void registerFileSets(@NotNull ScratchRootsEntity entity,
                               @NotNull WorkspaceFileSetRegistrar registrar,
                               @NotNull EntityStorage storage) {
    for (VirtualFileUrl root : entity.getRoots()) {
      registrar.registerFileSet(root, WorkspaceFileKind.CUSTOM, entity, null);
    }
  }
}
