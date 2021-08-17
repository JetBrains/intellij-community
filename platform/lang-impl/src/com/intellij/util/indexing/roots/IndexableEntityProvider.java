// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

@ApiStatus.Experimental
public interface IndexableEntityProvider {
  ExtensionPointName<IndexableEntityProvider> EP_NAME = new ExtensionPointName<>("com.intellij.indexableEntityProvider");

  /**
   * Provides iterators to index files after {@code entity} was added
   *
   * @param storage is initialized after the change happened. Be ready to situation when desired entity is missing
   */
  @NotNull
  Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull WorkspaceEntity entity,
                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                      @NotNull Project project) throws IndexableEntityResolvingException;

  /**
   * Provides iterators to index files after {@code oldEntity} was replaced with {@code newEntity}
   *
   * @param storage is initialized after the change happened. Be ready to situation when desired entity is missing
   */
  @NotNull
  Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull WorkspaceEntity oldEntity,
                                                                         @NotNull WorkspaceEntity newEntity,
                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                         @NotNull Project project) throws IndexableEntityResolvingException;

  /**
   * Provides iterators to index files after {@code entity} was removed
   *
   * @param storage is initialized after the change happened. Be ready to situation when desired entity is missing
   */
  @NotNull
  default Collection<? extends IndexableFilesIterator> getRemovedEntityIterator(@NotNull WorkspaceEntity entity,
                                                                                @NotNull WorkspaceEntityStorage storage,
                                                                                @NotNull Project project)
    throws IndexableEntityResolvingException {
    return Collections.emptyList();
  }
}
