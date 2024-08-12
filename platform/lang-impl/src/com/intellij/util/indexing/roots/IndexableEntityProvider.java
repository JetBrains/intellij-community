// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.jps.entities.ContentRootEntity;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;

/**
 * This extension point together with  {@link com.intellij.util.indexing.roots.builders.IndexableIteratorBuilderHandler} allows to control
 * indexing of Workspace Model entities and might be needed for custom entities in exotic corner cases.
 * {@link Enforced} interface allows enforcing reindexing of additional paths on entity change. Those paths are not directly associated
 * with the entity by its {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor}.
 * <p/>
 * {@link IndexableEntityProvider} which is not {@link Enforced} is effectively ignored.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public interface IndexableEntityProvider<E extends WorkspaceEntity> {
  ExtensionPointName<IndexableEntityProvider<? extends WorkspaceEntity>> EP_NAME =
    new ExtensionPointName<>("com.intellij.indexableEntityProvider");

  @NotNull
  Class<E> getEntityClass();

  default @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.emptyList();
  }

  /**
   * Provides builders of iterators to index files after {@code entity} was added
   */
  @NotNull
  Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull E entity,
                                                                                @NotNull Project project);

  /**
   * Provides builders of iterators to index files after {@code oldEntity} was replaced with {@code newEntity}
   */
  @NotNull
  Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull E oldEntity,
                                                                                   @NotNull E newEntity,
                                                                                   @NotNull Project project);

  /**
   * Provides builders of iterators to index files after {@code entity} was removed
   */
  default @NotNull Collection<? extends IndexableIteratorBuilder> getRemovedEntityIteratorBuilders(@NotNull E entity,
                                                                                          @NotNull Project project) {
    return Collections.emptyList();
  }

  /**
   * Currently for entities with bidirectional reference between (for example, {@link ModuleEntity} and
   * {@link ContentRootEntity}) Workspace Model allows
   * editing this reference on any side, and Replaced event would happen for that side only.
   * To support both ways of changing, consider using this interface.
   *
   * @param <P> parent entity class
   */
  interface DependencyOnParent<P extends WorkspaceEntity> {
    @NotNull
    Class<P> getParentClass();

    @NotNull
    Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull P oldEntity,
                                                                                     @NotNull P newEntity);

    static <E extends WorkspaceEntity> DependencyOnParent<E> create(@NotNull Class<E> parentClass,
                                                                    @NotNull BiFunction<? super E, ? super E, @NotNull Collection<? extends IndexableIteratorBuilder>> replacedIteratorsCreator) {
      final class MyDependency implements DependencyOnParent<E> {
        @Override
        public @NotNull Class<E> getParentClass() {
          return parentClass;
        }

        @Override
        public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull E oldEntity,
                                                                                                         @NotNull E newEntity) {
          return replacedIteratorsCreator.apply(oldEntity, newEntity);
        }
      }
      return new MyDependency();
    }
  }

  /**
   * @deprecated This interface is deprecated and will be removed in a future release. For creation of iterators
   * for existing {@link WorkspaceEntity} {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor} is used.
   */
  @Deprecated(forRemoval = true)
  interface Existing<E extends WorkspaceEntity> extends IndexableEntityProvider<E> {

    /**
     * Provides builders for iterators to index files when just project is indexed, no events given
     */
    default @NotNull Collection<? extends IndexableIteratorBuilder> getExistingEntityIteratorBuilder(@NotNull E entity,
                                                                                            @NotNull Project project) {
      return getAddedEntityIteratorBuilders(entity, project);
    }

    /**
     * Provides builders for iterators to index files belonging to a module when just module content is indexed, no events given
     */
    @NotNull
    Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                        @NotNull EntityStorage entityStorage,
                                                                                        @NotNull Project project);
  }

  /**
   * Idea behind this marker interface is to mark that something should be reindexed as cheap as possible,
   * with expensive checks and merges made in batch in corresponding
   * {@link com.intellij.util.indexing.roots.builders.IndexableIteratorBuilderHandler#instantiate(Collection, Project, EntityStorage)}
   */
  interface IndexableIteratorBuilder {
  }

  /**
   * Marks providers that should be used to determine the scope of reindexing on Workspace model changes even after switching to
   * {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor}
   */
  interface Enforced<E extends WorkspaceEntity> extends IndexableEntityProvider<E> {
  }
}
