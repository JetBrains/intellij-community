// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeIndexingInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

class EntityIndexingServiceImpl implements EntityIndexingService {
  private static final Logger LOG = Logger.getInstance(EntityIndexingServiceImpl.class);
  private static final RootChangesLogger ROOT_CHANGES_LOGGER = new RootChangesLogger();

  @Override
  public void indexChanges(@NotNull Project project, @NotNull List<? extends RootsChangeIndexingInfo> changes) {
    if (!(FileBasedIndex.getInstance() instanceof FileBasedIndexImpl)) return;
    if (LightEdit.owns(project)) return;
    if (changes.isEmpty()) {
      runFullReindex(project, "Project roots have changed");
    }
    if (Registry.is("indexing.full.rescan.on.workspace.model.changes")) {
      runFullReindex(project, "Reindex requested by project root model changes (full rescanning forced by registry key)");
      return;
    }
    for (RootsChangeIndexingInfo change : changes) {
      if (change == RootsChangeIndexingInfo.TOTAL_REINDEX) {
        runFullReindex(project, "Reindex requested by project root model changes");
        return;
      }
    }
    List<IndexableIteratorBuilder> builders = new SmartList<>();
    WorkspaceEntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    for (RootsChangeIndexingInfo change : changes) {
      if (change == RootsChangeIndexingInfo.NO_INDEXING_NEEDED) continue;
      if (change instanceof ProjectRootsChangeListener.WorkspaceEventIndexingInfo) {
        builders.addAll(getBuildersOnWorkspaceChange(project, ((ProjectRootsChangeListener.WorkspaceEventIndexingInfo)change).getEvents()));
      }
      else {
        LOG.warn("Unexpected change " + change.getClass() + " " + change + ", full reindex requested");
        runFullReindex(project, "Reindex on unexpected change in EntityIndexingServiceImpl");
        return;
      }
    }

    if (!builders.isEmpty()) {
      List<IndexableFilesIterator> mergedIterators =
        IndexableIteratorBuilders.INSTANCE.instantiateBuilders(builders, project, entityStorage);

      List<String> debugNames = ContainerUtil.map(mergedIterators, it -> it.getDebugName());
      LOG.debug("Accumulated iterators: " + debugNames);
      int maxNamesToLog = 10;
      String reasonMessage = "changes in: " + debugNames
        .stream()
        .limit(maxNamesToLog)
        .map(n -> StringUtil.wrapWithDoubleQuote(n)).collect(Collectors.joining(", "));
      if (debugNames.size() > maxNamesToLog) {
        reasonMessage += " and " + (debugNames.size() - maxNamesToLog) + " iterators more";
      }
      logRootChanges(project, false);
      new UnindexedFilesUpdater(project, mergedIterators, reasonMessage).queue(project);
    }
  }

  private static void runFullReindex(@NotNull Project project, @NotNull @NonNls String reason) {
    logRootChanges(project, true);
    new UnindexedFilesUpdater(project, reason).queue(project);
  }


  private static void logRootChanges(@NotNull Project project, boolean isFullReindex) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (LOG.isDebugEnabled()) {
        String message = isFullReindex ?
                         "Project roots of " + project.getName() + " have changed" :
                         "Project roots of " + project.getName() + " will be partially reindexed";
        LOG.debug(message, new Throwable());
      }
    }
    else {
      ROOT_CHANGES_LOGGER.info(project, isFullReindex);
    }
  }

  @TestOnly
  @NotNull
  static List<IndexableFilesIterator> getIterators(@NotNull Project project,
                                                   @NotNull Collection<EntityChange<?>> events) {
    WorkspaceEntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    List<IndexableIteratorBuilder> result = getBuildersOnWorkspaceChange(project, events);
    return IndexableIteratorBuilders.INSTANCE.instantiateBuilders(result, project, entityStorage);
  }

  @NotNull
  private static List<IndexableIteratorBuilder> getBuildersOnWorkspaceChange(@NotNull Project project,
                                                                             @NotNull Collection<EntityChange<?>> event) {
    List<IndexableIteratorBuilder> builders = new SmartList<>();
    for (EntityChange<? extends WorkspaceEntity> change : event) {
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