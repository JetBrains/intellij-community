// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeIndexingInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.ModuleIndexableFilesIteratorImpl;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.VersionedStorageChange;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

class EntityIndexingServiceImpl implements EntityIndexingService {
  private static final Logger LOG = Logger.getInstance(EntityIndexingServiceImpl.class);

  @Override
  public void indexChanges(@NotNull Project project, @NotNull List<? extends RootsChangeIndexingInfo> changes) {
    if (!(FileBasedIndex.getInstance() instanceof FileBasedIndexImpl)) return;
    if (Registry.is("indexing.full.rescan.on.workspace.model.changes")) {
      new UnindexedFilesUpdater(project, "Reindex requested by project root model changes (full rescanning forced by registry key)").queue(project);
      return;
    }
    for (RootsChangeIndexingInfo change : changes) {
      if (change == RootsChangeIndexingInfo.TOTAL_REINDEX) {
        new UnindexedFilesUpdater(project, "Reindex requested by project root model changes").queue(project);
        return;
      }
    }
    List<IndexableIteratorBuilder> builders = new SmartList<>();
    WorkspaceEntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    for (RootsChangeIndexingInfo change : changes) {
      if (change == RootsChangeIndexingInfo.NO_INDEXING_NEEDED) continue;
      if (change instanceof ProjectRootsChangeListener.WorkspaceEventIndexingInfo) {
        builders.addAll(getBuildersOnWorkspaceChange(project, ((ProjectRootsChangeListener.WorkspaceEventIndexingInfo)change).getEvent()));
      }
      else {
        LOG.warn("Unexpected change " + change.getClass() + " " + change + ", full reindex requested");
        DumbService.getInstance(project)
          .queueTask(new UnindexedFilesUpdater(project, "Reindex on unexpected change in EntityIndexingServiceImpl"));
        return;
      }
    }

    if (!builders.isEmpty()) {
      List<IndexableFilesIterator> mergedIterators =
        IndexableIteratorBuilders.INSTANCE.instantiateBuilders(builders, project, entityStorage);
      StringBuilder sb = new StringBuilder("Accumulated iterators:");

      for (IndexableFilesIterator iterator : mergedIterators) {
        sb.append('\n');
        if (iterator instanceof ModuleIndexableFilesIteratorImpl) {
          sb.append(((ModuleIndexableFilesIteratorImpl)iterator).getDebugDescription());
        }
        else {
          sb.append(iterator);
        }
      }
      LOG.debug(sb.toString());
      DumbService.getInstance(project)
        .queueTask(new UnindexedFilesUpdater(project, mergedIterators, "Reindex on accumulated partial changes"));
    }
  }

  @TestOnly
  @NotNull
  static List<IndexableFilesIterator> getIterators(@NotNull Project project,
                                                   @NotNull Collection<? extends VersionedStorageChange> events) {
    WorkspaceEntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    List<IndexableIteratorBuilder> result = new ArrayList<>(events.size());
    for (VersionedStorageChange event : events) {
      result.addAll(getBuildersOnWorkspaceChange(project, event));
    }
    return IndexableIteratorBuilders.INSTANCE.instantiateBuilders(result, project, entityStorage);
  }

  @NotNull
  private static List<IndexableIteratorBuilder> getBuildersOnWorkspaceChange(@NotNull Project project,
                                                                             @NotNull VersionedStorageChange event) {
    List<IndexableIteratorBuilder> builders = new SmartList<>();
    Iterator<EntityChange<?>> iterator = event.getAllChanges().iterator();
    while (iterator.hasNext()) {
      EntityChange<? extends WorkspaceEntity> change = iterator.next();
      if (change instanceof EntityChange.Added) {
        WorkspaceEntity entity = ((EntityChange.Added<? extends WorkspaceEntity>)change).getEntity();
        collectIteratorBuildersOnAdd(entity, project, builders);
      }
      else if (change instanceof EntityChange.Replaced) {
        WorkspaceEntity newEntity = ((EntityChange.Replaced<? extends WorkspaceEntity>)change).getNewEntity();
        WorkspaceEntity oldEntity = ((EntityChange.Replaced<? extends WorkspaceEntity>)change).getOldEntity();
        collectIteratorBuildersOnReplace(oldEntity, newEntity, project, builders);
      }
      else if (change instanceof EntityChange.Removed) {
        WorkspaceEntity entity = ((EntityChange.Removed<? extends WorkspaceEntity>)change).getEntity();
        collectIteratorBuildersOnRemove(entity, project, builders);
      }
      else {
        LOG.error("Unexpected change " + change.getClass() + " " + change);
      }
    }
    return builders;
  }

  private static <E extends WorkspaceEntity> void collectIteratorBuildersOnAdd(@NotNull E entity,
                                                                               @NotNull Project project,
                                                                               @NotNull Collection<IndexableIteratorBuilder> builders) {
    Class<? extends WorkspaceEntity> entityClass = entity.getClass();
    for (IndexableEntityProvider<?> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (entityClass == provider.getEntityClass()) {
        //noinspection unchecked
        builders.addAll(((IndexableEntityProvider<E>)provider).getAddedEntityIteratorBuilders(entity, project));
      }
    }
  }

  private static <E extends WorkspaceEntity> void collectIteratorBuildersOnReplace(@NotNull E oldEntity,
                                                                                   @NotNull E newEntity,
                                                                                   @NotNull Project project,
                                                                                   @NotNull Collection<IndexableIteratorBuilder> builders) {
    Class<? extends WorkspaceEntity> entityClass = oldEntity.getClass();
    for (IndexableEntityProvider<?> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (entityClass == provider.getEntityClass()) {
        //noinspection unchecked
        builders.addAll(
          ((IndexableEntityProvider<E>)provider).getReplacedEntityIteratorBuilders(oldEntity, newEntity));
      }
    }
    if (oldEntity instanceof ModuleEntity) {
      ModuleEntity oldModule = (ModuleEntity)oldEntity;
      ModuleEntity newModule = (ModuleEntity)newEntity;
      for (IndexableEntityProvider<?> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
        if (provider instanceof IndexableEntityProvider.ModuleEntityDependent) {
          builders.addAll(((IndexableEntityProvider.ModuleEntityDependent<?>)provider).
                            getReplacedModuleEntityIteratorBuilder(oldModule, newModule, project));
        }
      }
    }
  }

  private static <E extends WorkspaceEntity> void collectIteratorBuildersOnRemove(@NotNull E entity,
                                                                                  @NotNull Project project,
                                                                                  @NotNull Collection<IndexableIteratorBuilder> builders) {
    Class<? extends WorkspaceEntity> entityClass = entity.getClass();
    for (IndexableEntityProvider<?> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (entityClass == provider.getEntityClass()) {
        //noinspection unchecked
        builders.addAll(((IndexableEntityProvider<E>)provider).getRemovedEntityIteratorBuilders(entity, project));
      }
    }
  }
}