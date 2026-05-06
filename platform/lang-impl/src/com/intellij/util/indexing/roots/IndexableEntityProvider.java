// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;

/**
 * This extension point together with  {@link com.intellij.util.indexing.roots.builders.IndexableIteratorBuilderHandler} allows to control
 * indexing of Workspace Model entities and might be needed for custom entities in exotic corner cases.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public interface IndexableEntityProvider<E extends WorkspaceEntity> {

  /**
   * Idea behind this marker interface is to mark that something should be reindexed as cheap as possible,
   * with expensive checks and merges made in batch in corresponding
   * {@link com.intellij.util.indexing.roots.builders.IndexableIteratorBuilderHandler#instantiate(Collection, Project, EntityStorage)}
   */
  interface IndexableIteratorBuilder {
  }
}
