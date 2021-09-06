// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

@ApiStatus.Experimental
public interface IndexableEntityProvider<E extends WorkspaceEntity> {
  ExtensionPointName<IndexableEntityProvider<? extends WorkspaceEntity>> EP_NAME =
    new ExtensionPointName<>("com.intellij.indexableEntityProvider");

  @NotNull
  Class<E> getEntityClass();

  /**
   * Provides iterators to index files after {@code entity} was added
   *
   * @param storage is initialized after the change happened. Be ready to situation when desired entity is missing
   */
  @NotNull
  Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull E entity,
                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                      @NotNull Project project);

  /**
   * Provides iterators to index files after {@code oldEntity} was replaced with {@code newEntity}
   *
   * @param storage is initialized after the change happened. Be ready to situation when desired entity is missing
   */
  @NotNull
  Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull E oldEntity,
                                                                         @NotNull E newEntity,
                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                         @NotNull Project project);

  /**
   * Provides iterators to index files after {@code entity} was removed
   *
   * @param storage is initialized after the change happened. Be ready to situation when desired entity is missing
   */
  @NotNull
  default Collection<? extends IndexableFilesIterator> getRemovedEntityIterator(@NotNull E entity,
                                                                                @NotNull WorkspaceEntityStorage storage,
                                                                                @NotNull Project project) {
    return Collections.emptyList();
  }

  /**
   * Should be used, when change related to desired WorkspaceEntity is visible as ModuleEntity change,
   * for example, adding content root is visible as ModuleEntityChange, see {@link ContentRootIndexableEntityProvider}.
   */
  interface ModuleEntityDependent<E extends WorkspaceEntity> extends IndexableEntityProvider<E> {
    /**
     * Provides iterators to index files after {@code oldEntity} was replaced with {@code newEntity}
     *
     * @param storage is initialized after the change happened. Be ready to situation when desired entity is missing
     */
    @NotNull
    Collection<? extends IndexableFilesIterator> getReplacedModuleEntityIterator(@NotNull ModuleEntity oldEntity,
                                                                                 @NotNull ModuleEntity newEntity,
                                                                                 @NotNull WorkspaceEntityStorage storage,
                                                                                 @NotNull Project project);
  }

  interface Existing<E extends WorkspaceEntity> extends IndexableEntityProvider<E> {
    /**
     * Provides iterators to index files when just project is indexed, no events given
     */
    @NotNull
    default Collection<? extends IndexableFilesIterator> getExistingEntityIterator(@NotNull E entity,
                                                                                   @NotNull WorkspaceEntityStorage storage,
                                                                                   @NotNull Project project) {
      return getAddedEntityIterator(entity, storage, project);
    }


    /**
     * Provides iterators to index files belonging to a module when just module content is indexed, no events given
     */
    @NotNull
    Collection<? extends IndexableFilesIterator> getIteratorsForExistingModule(@NotNull ModuleEntity entity,
                                                                               @NotNull WorkspaceEntityStorage entityStorage,
                                                                               @NotNull Project project);
  }
}
