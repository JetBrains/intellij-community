// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeIndexingInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.util.indexing.roots.IndexableEntityResolvingException;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.ModuleIndexableFilesIteratorImpl;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.VersionedStorageChange;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

class EntityIndexingServiceImpl implements EntityIndexingService {
  private static final Logger LOG = Logger.getInstance(EntityIndexingServiceImpl.class);

  @Override
  public void indexChanges(@NotNull Project project, @NotNull List<RootsChangeIndexingInfo> changes) {
    if (!(FileBasedIndex.getInstance() instanceof FileBasedIndexImpl)) return;
    if (Registry.is("indexing.full.rescan.on.workspace.model.changes")) {
      DumbService.getInstance(project).queueTask(new UnindexedFilesUpdater(project, "Reindex due to registry setting"));
      return;
    }
    for (RootsChangeIndexingInfo change : changes) {
      if (change == RootsChangeIndexingInfo.TOTAL_REINDEX) {
        DumbService.getInstance(project).queueTask(new UnindexedFilesUpdater(project, "Reindex requested by changes"));
        return;
      }
    }
    List<IndexableFilesIterator> iterators = new SmartList<>();
    WorkspaceEntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    for (RootsChangeIndexingInfo change : changes) {
      if (change == RootsChangeIndexingInfo.NO_INDEXING_NEEDED) continue;
      if (change instanceof ProjectRootsChangeListener.WorkspaceEventIndexingInfo) {
        try {
          Collection<? extends IndexableFilesIterator> iteratorsFromWorkspaceChange =
            getIteratorsWorkspaceChange(project, ((ProjectRootsChangeListener.WorkspaceEventIndexingInfo)change).getEvent(), entityStorage);
          iterators.addAll(iteratorsFromWorkspaceChange);
        }
        catch (IndexableEntityResolvingException e) {
          LOG.warn(e);
          DumbService.getInstance(project)
            .queueTask(new UnindexedFilesUpdater(project, "Reindex on IndexableEntityResolvingException in EntityIndexingServiceImpl"));
          return;
        }
      }
      else {
        LOG.warn("Unexpected change " + change.getClass() + " " + change + ", full reindex requested");
        DumbService.getInstance(project)
          .queueTask(new UnindexedFilesUpdater(project, "Reindex on unexpected change in EntityIndexingServiceImpl"));
        return;
      }
    }

    if (!iterators.isEmpty()) {
      iterators = mergeIterators(iterators);
      StringBuilder sb = new StringBuilder("Accumulated iterators:");

      for (IndexableFilesIterator iterator : iterators) {
        sb.append('\n');
        if (iterator instanceof ModuleIndexableFilesIteratorImpl) {
          sb.append(((ModuleIndexableFilesIteratorImpl)iterator).getDebugDescription());
        }
        else {
          sb.append(iterator);
        }
      }
      LOG.debug(sb.toString());
      DumbService.getInstance(project).queueTask(new UnindexedFilesUpdater(project, iterators, "Reindex on accumulated partial changes"));
    }
  }

  @TestOnly
  @NotNull
  static List<IndexableFilesIterator> getIterators(@NotNull Project project,
                                                   @NotNull Collection<? extends VersionedStorageChange> events)
    throws IndexableEntityResolvingException {
    WorkspaceEntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    List<IndexableFilesIterator> result = new ArrayList<>(events.size());
    for (VersionedStorageChange event : events) {
      result.addAll(getIteratorsWorkspaceChange(project, event, entityStorage));
    }
    return mergeIterators(result);
  }

  @NotNull
  private static List<IndexableFilesIterator> getIteratorsWorkspaceChange(@NotNull Project project,
                                                                          @NotNull VersionedStorageChange event,
                                                                          WorkspaceEntityStorage entityStorage)
    throws IndexableEntityResolvingException {
    List<IndexableFilesIterator> iterators = new SmartList<>();
    Iterator<EntityChange<?>> iterator = event.getAllChanges().iterator();
    while (iterator.hasNext()) {
      EntityChange<? extends WorkspaceEntity> change = iterator.next();
      if (change instanceof EntityChange.Added) {
        WorkspaceEntity entity = ((EntityChange.Added<? extends WorkspaceEntity>)change).getEntity();
        for (IndexableEntityProvider provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
          iterators.addAll(provider.getAddedEntityIterator(entity, entityStorage, project));
        }
      }
      else if (change instanceof EntityChange.Replaced) {
        WorkspaceEntity newEntity = ((EntityChange.Replaced<? extends WorkspaceEntity>)change).getNewEntity();
        WorkspaceEntity oldEntity = ((EntityChange.Replaced<? extends WorkspaceEntity>)change).getOldEntity();
        for (IndexableEntityProvider provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
          iterators.addAll(provider.getReplacedEntityIterator(oldEntity, newEntity, entityStorage, project));
        }
      }
      else if (change instanceof EntityChange.Removed) {
        WorkspaceEntity entity = ((EntityChange.Removed<? extends WorkspaceEntity>)change).getEntity();
        for (IndexableEntityProvider provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
          iterators.addAll(provider.getRemovedEntityIterator(entity, entityStorage, project));
        }
      }
      else {
        LOG.error("Unexpected change " + change.getClass() + " " + change);
      }
    }
    return iterators;
  }

  private static @NotNull List<IndexableFilesIterator> mergeIterators(List<IndexableFilesIterator> iterators) {
    List<IndexableFilesIterator> result = new ArrayList<>(iterators.size());
    Collection<ModuleIndexableFilesIteratorImpl> rootIterators = new ArrayList<>();
    Set<IndexableSetOrigin> origins = new HashSet<>();
    for (IndexableFilesIterator iterator : iterators) {
      if (iterator instanceof ModuleIndexableFilesIteratorImpl) {
        rootIterators.add((ModuleIndexableFilesIteratorImpl)iterator);
      }
      else {
        if (origins.add(iterator.getOrigin())) {
          result.add(iterator);
        }
      }
    }
    result.addAll(ModuleIndexableFilesIteratorImpl.getMergedIterators(rootIterators));
    return result;
  }
}