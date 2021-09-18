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
   * Provides builders of iterators to index files after {@code entity} was added
   */
  @NotNull
  Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull E entity,
                                                                                @NotNull Project project);

  /**
   * Provides builders of iterators to index files after {@code oldEntity} was replaced with {@code newEntity}
   *
   */
  @NotNull
  Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull E oldEntity,
                                                                                   @NotNull E newEntity);

  /**
   * Provides builders of iterators to index files after {@code entity} was removed
   *
   */
  @NotNull
  default Collection<? extends IndexableIteratorBuilder> getRemovedEntityIteratorBuilders(@NotNull E entity,
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
     */
    @NotNull
    Collection<? extends IndexableIteratorBuilder> getReplacedModuleEntityIteratorBuilder(@NotNull ModuleEntity oldEntity,
                                                                                          @NotNull ModuleEntity newEntity,
                                                                                          @NotNull Project project);
  }

  interface Existing<E extends WorkspaceEntity> extends IndexableEntityProvider<E> {

    /**
     * Provides builders for iterators to index files when just project is indexed, no events given
     */
    @NotNull
    default Collection<? extends IndexableIteratorBuilder> getExistingEntityIteratorBuilder(@NotNull E entity,
                                                                                            @NotNull Project project) {
      return getAddedEntityIteratorBuilders(entity, project);
    }

    /**
     * Provides builders for iterators to index files belonging to a module when just module content is indexed, no events given
     */
    @NotNull
    Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                        @NotNull WorkspaceEntityStorage entityStorage,
                                                                                        @NotNull Project project);
  }

  /**
   * Idea behind this marker interface is to mark that something should be reindexed as cheap as possible,
   * with expensive checks and merges made in batch in corresponding
   * {@link com.intellij.util.indexing.roots.builders.IndexableIteratorBuilderHandler#instantiate(Collection, Project, WorkspaceEntityStorage)}
   */
  interface IndexableIteratorBuilder {
  }
}
