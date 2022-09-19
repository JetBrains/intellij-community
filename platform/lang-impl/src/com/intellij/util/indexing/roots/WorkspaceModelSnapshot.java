// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.roots.kind.IndexableSetSelfDependentOrigin;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.storage.*;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.SourceRootEntity;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class WorkspaceModelSnapshot {
  final ImmutableMap<WorkspaceEntity, Collection<IndexableSetSelfDependentOrigin>> entitiesToOrigins;

  private WorkspaceModelSnapshot(@NotNull ImmutableSetMultimap.Builder<WorkspaceEntity, IndexableSetSelfDependentOrigin> builder) {
    this.entitiesToOrigins = builder.build().asMap();
  }

  WorkspaceModelSnapshot(@NotNull Project project) {
    this(createBuilder(project));
  }

  private static ImmutableSetMultimap.Builder<WorkspaceEntity, IndexableSetSelfDependentOrigin> createBuilder(@NotNull Project project) {
    EntityStorage storage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    ImmutableSetMultimap.Builder<WorkspaceEntity, IndexableSetSelfDependentOrigin> builder = new ImmutableSetMultimap.Builder<>();
    for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (!(provider instanceof IndexableEntityProvider.ExistingEx<?>)) {
        continue;
      }
      handleProvider((IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>)provider, storage, project, builder);
    }
    return builder;
  }

  private static <E extends WorkspaceEntity> void handleProvider(@NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                 @NotNull EntityStorage storage,
                                                                 @NotNull Project project,
                                                                 @NotNull ImmutableSetMultimap.Builder<WorkspaceEntity, IndexableSetSelfDependentOrigin> entities) {
    Class<E> aClass = provider.getEntityClass();
    for (E entity : SequencesKt.asIterable(storage.entities(aClass))) {
      addOrigins(entities, entity, provider, storage, project);
    }
  }

  @Nullable
  WorkspaceModelSnapshot createChangedIfNeeded(@NotNull VersionedStorageChange storageChange,
                                               @NotNull Project project) {
    EntityStorageSnapshot storage = storageChange.getStorageAfter();
    MultiMap<WorkspaceEntity, IndexableSetSelfDependentOrigin> toAdd = MultiMap.createSet();
    MultiMap<WorkspaceEntity, IndexableSetSelfDependentOrigin> toRemove = MultiMap.createSet();
    for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof IndexableEntityProvider.ExistingEx<?>) {
        handleWorkspaceModelChange(toAdd, toRemove, storageChange,
                                   (IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>)provider,
                                   storage, project);
      }
    }
    if (toAdd.isEmpty() && toRemove.isEmpty()) {
      return null;
    }
    ImmutableSetMultimap.Builder<WorkspaceEntity, IndexableSetSelfDependentOrigin> copy = new ImmutableSetMultimap.Builder<>();
    for (Map.Entry<WorkspaceEntity, Collection<IndexableSetSelfDependentOrigin>> entry : entitiesToOrigins.entrySet()) {
      WorkspaceEntity entity = entry.getKey();
      Collection<IndexableSetSelfDependentOrigin> add = toAdd.remove(entity);
      Collection<IndexableSetSelfDependentOrigin> remove = toRemove.get(entity);
      if (add == null && remove.isEmpty()) {
        copy.putAll(entity, entry.getValue());
      }
      else {
        Collection<IndexableSetSelfDependentOrigin> origins = new HashSet<>(entry.getValue());
        origins.removeAll(remove);
        if (add != null) {
          origins.addAll(add);
        }
        copy.putAll(entity, origins);
      }
    }
    for (Map.Entry<WorkspaceEntity, Collection<IndexableSetSelfDependentOrigin>> entry : toAdd.entrySet()) {
      copy.putAll(entry.getKey(), entry.getValue());
    }
    return new WorkspaceModelSnapshot(copy);
  }

  private static <E extends WorkspaceEntity> void handleWorkspaceModelChange(@NotNull MultiMap<WorkspaceEntity, IndexableSetSelfDependentOrigin> toAdd,
                                                                             @NotNull MultiMap<WorkspaceEntity, IndexableSetSelfDependentOrigin> toRemove,
                                                                             @NotNull VersionedStorageChange storageChange,
                                                                             @NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                             @NotNull EntityStorageSnapshot storage,
                                                                             @NotNull Project project) {
    List<EntityChange<E>> changes = storageChange.getChanges(provider.getEntityClass());
    for (EntityChange<E> change : changes) {
      if (change instanceof EntityChange.Added<E>) {
        E entity = ((EntityChange.Added<E>)change).getEntity();
        addOrigins(toAdd, entity, provider, storage, project);
      }
      else if (change instanceof EntityChange.Replaced<E>) {
        E oldEntity = Objects.requireNonNull(change.getOldEntity());
        addOrigins(toRemove, oldEntity, provider, storage, project);

        E newEntity = ((EntityChange.Replaced<E>)change).getNewEntity();
        addOrigins(toAdd, newEntity, provider, storage, project);
      }
      else if (change instanceof EntityChange.Removed<E>) {
        E entity = ((EntityChange.Removed<E>)change).getEntity();
        addOrigins(toRemove, entity, provider, storage, project);
      }
      else {
        throw new IllegalStateException("Unexpected change " + change.getClass());
      }
    }
    if (provider instanceof ContentRootIndexableEntityProvider) {
      SourceRootIndexableEntityProvider sourceRootProvider =
        IndexableEntityProvider.EP_NAME.findExtensionOrFail(SourceRootIndexableEntityProvider.class);
      for (EntityChange<E> change : changes) {
        if (change instanceof EntityChange.Replaced<E>) {
          ContentRootEntity oldEntity = Objects.requireNonNull(((EntityChange.Replaced<ContentRootEntity>)change).getOldEntity());
          for (SourceRootEntity sourceRootEntity : oldEntity.getSourceRoots()) {
            addOrigins(toRemove, sourceRootEntity, sourceRootProvider, storage, project);
          }

          ContentRootEntity newEntity = ((EntityChange.Replaced<ContentRootEntity>)change).getNewEntity();
          for (SourceRootEntity sourceRootEntity : newEntity.getSourceRoots()) {
            addOrigins(toAdd, sourceRootEntity, sourceRootProvider, storage, project);
          }
        }
      }
    }
  }

  private static <E extends WorkspaceEntity> void addOrigins(@NotNull ImmutableSetMultimap.Builder<WorkspaceEntity, IndexableSetSelfDependentOrigin> entities,
                                                             @NotNull E entity,
                                                             @NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                             @NotNull EntityStorage storage,
                                                             @NotNull Project project) {
    Collection<? extends IndexableSetSelfDependentOrigin> origins = provider.getExistingEntityIteratorOrigins(entity, storage, project);
    if (!origins.isEmpty()) {
      entities.putAll(entity, origins);
    }
  }

  private static <E extends WorkspaceEntity> void addOrigins(@NotNull MultiMap<WorkspaceEntity, IndexableSetSelfDependentOrigin> entities,
                                                             @NotNull E entity,
                                                             @NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                             @NotNull EntityStorage storage,
                                                             @NotNull Project project) {
    Collection<? extends IndexableSetSelfDependentOrigin> origins = provider.getExistingEntityIteratorOrigins(entity, storage, project);
    if (!origins.isEmpty()) {
      entities.putValues(entity, origins);
    }
  }

  @Nullable
  WorkspaceModelSnapshot createWithRefreshedEntitiesIfNeeded(@NotNull List<? extends WorkspaceEntity> entities,
                                                             @NotNull Project project) {
    if (entities.isEmpty()) return null;
    EntityStorage storage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    MultiMap<WorkspaceEntity, IndexableSetSelfDependentOrigin> refreshed = MultiMap.createSet();
    for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (provider instanceof IndexableEntityProvider.ExistingEx<?>) {
        handleEntitiesRefresh((IndexableEntityProvider.ExistingEx<?>)provider, entities, project, storage, refreshed);
      }
    }
    if (refreshed.isEmpty()) return null;
    ImmutableSetMultimap.Builder<WorkspaceEntity, IndexableSetSelfDependentOrigin> result = new ImmutableSetMultimap.Builder<>();
    for (Map.Entry<WorkspaceEntity, Collection<IndexableSetSelfDependentOrigin>> entry : entitiesToOrigins.entrySet()) {
      if (refreshed.containsKey(entry.getKey())) {
        result.putAll(entry.getKey(), refreshed.get(entry.getKey()));
      }
      else {
        result.putAll(entry.getKey(), entry.getValue());
      }
    }
    return new WorkspaceModelSnapshot(result);
  }

  private static <E extends WorkspaceEntity> void handleEntitiesRefresh(@NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                        @NotNull List<? extends WorkspaceEntity> entities,
                                                                        @NotNull Project project,
                                                                        @NotNull EntityStorage storage,
                                                                        @NotNull MultiMap<WorkspaceEntity, IndexableSetSelfDependentOrigin> entitiesMap) {
    Class<E> aClass = provider.getEntityClass();
    for (WorkspaceEntity entity : entities) {
      if (aClass.isInstance(entity)) {
        //noinspection unchecked
        Collection<? extends IndexableSetSelfDependentOrigin> origins =
          provider.getExistingEntityIteratorOrigins((E)entity, storage, project);
        entitiesMap.putValues(entity, origins);
      }
    }
  }
}
