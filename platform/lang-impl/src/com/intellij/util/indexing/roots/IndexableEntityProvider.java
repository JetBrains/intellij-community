// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.IndexableFilesIndex;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;

/**
 * This extension point together with  {@link com.intellij.util.indexing.roots.builders.IndexableIteratorBuilderHandler} allows to control
 * indexing of Workspace Model entities and might be needed for custom entities.
 * <p>
 * <ol>Basic scenario is as following:
 *  <li>either whole project is reindexed, or some change events were created by WorkspaceModel.
 *  <ul>
 *    <li>In the first case all entities in EntityStorage are fed into {@link Existing} extension points to get {@link IndexableIteratorBuilder}s.</li>
 *    <li>In the second case all events are fed into {@link IndexableEntityProvider} extension points to get {@link IndexableIteratorBuilder}s
 * again.
 *    <ul><li>
 *     For entities with bidirectional reference between, like {@link ModuleEntity} and
 *     {@link com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity}, Workspace model allows changing that reference
 *     on any of the sides, but issues Replace event only for that side. To enforce handling both cases, consider using
 *     {@link IndexableEntityProvider::getDependencies} for such entities.
 *    </li></ul>
 *    </li>
 *   </ul></il>
 *   <li>After all {@link IndexableIteratorBuilder}s are collected, they are merged to avoid double indexing and transformed
 *   into {@link IndexableFilesIterator}s. Merging and transformation is made
 *   by {@link com.intellij.util.indexing.roots.builders.IndexableIteratorBuilderHandler}s. Refer to its javadoc for more info.
 *   </li>
 *   <li>As the last step iterators are iterated by indexing system, indexes values are computed for the files they provide and
 *   saved for future usage.</li>
 * </ol>
 */
@ApiStatus.Experimental
public interface IndexableEntityProvider<E extends WorkspaceEntity> {
  ExtensionPointName<IndexableEntityProvider<? extends WorkspaceEntity>> EP_NAME =
    new ExtensionPointName<>("com.intellij.indexableEntityProvider");

  @NotNull
  Class<E> getEntityClass();

  @NotNull
  default Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
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
  @NotNull
  default Collection<? extends IndexableIteratorBuilder> getRemovedEntityIteratorBuilders(@NotNull E entity,
                                                                                          @NotNull Project project) {
    return Collections.emptyList();
  }

  /**
   * Currently for entities with bidirectional reference between (for example, {@link ModuleEntity} and
   * {@link com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity}) Workspace Model allows
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
      class MyDependency implements DependencyOnParent<E> {
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
   * Marks providers that should be used to determine scope of reindexing on Workspace model changes even after switching to
   * {@link com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor} ({@link IndexableFilesIndex#isEnabled()})
   */
  interface Enforced<E extends WorkspaceEntity> extends IndexableEntityProvider<E> {
  }
}
