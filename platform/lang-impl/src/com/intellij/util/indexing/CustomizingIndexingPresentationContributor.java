// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.indexing.roots.IndexableIteratorPresentation;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides opportunity to specify UI and diagnostic messages for indexing entities.
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
@ApiStatus.Experimental
public interface CustomizingIndexingPresentationContributor<E extends WorkspaceEntity> extends WorkspaceFileIndexContributor<E> {

  @Nullable
  IndexableIteratorPresentation customizeIteratorPresentation(@NotNull E entity);
}
