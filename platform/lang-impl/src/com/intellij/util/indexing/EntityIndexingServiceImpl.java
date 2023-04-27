// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService.StatusMark;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.LibraryIndexableEntityProvider;
import com.intellij.util.indexing.roots.WorkspaceIndexingRootsBuilder;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription;
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.impl.PlatformInternalWorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId;
import kotlin.Pair;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class EntityIndexingServiceImpl implements EntityIndexingServiceEx {
  private static final Logger LOG = Logger.getInstance(EntityIndexingServiceImpl.class);
  private static final RootChangesLogger ROOT_CHANGES_LOGGER = new RootChangesLogger();
  @NotNull
  private final CustomEntitiesCausingReindexTracker tracker = new CustomEntitiesCausingReindexTracker();

  @Override
  public void indexChanges(@NotNull Project project, @NotNull List<? extends RootsChangeRescanningInfo> changes) {
    if (!(FileBasedIndex.getInstance() instanceof FileBasedIndexImpl)) return;
    if (LightEdit.owns(project)) return;
    if (changes.isEmpty()) {
      runFullRescan(project, "Project roots have changed");
    }
    boolean indexDependencies = false;
    for (RootsChangeRescanningInfo change : changes) {
      if (change == RootsChangeRescanningInfo.TOTAL_RESCAN) {
        runFullRescan(project, "Reindex requested by project root model changes");
        return;
      }
      else if (change == RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED) {
        if (!indexDependencies && !DependenciesIndexedStatusService.shouldBeUsed()) {
          runFullRescan(project, "Reindex of changed dependencies requested, but not enabled");
          return;
        }
        else {
          indexDependencies = true;
        }
      }
    }
    List<IndexableIteratorBuilder> builders = new SmartList<>();

    StatusMark dependenciesStatusMark = null;
    if (indexDependencies) {
      Pair<Collection<? extends IndexableIteratorBuilder>, StatusMark> dependencyBuildersPair =
        DependenciesIndexedStatusService.getInstance(project).getDeltaWithLastIndexedStatus();
      if (dependencyBuildersPair == null) {
        runFullRescan(project, "Reindex of changed dependencies requested, but status is not initialized");
        return;
      }
      builders.addAll(dependencyBuildersPair.getFirst());
      dependenciesStatusMark = dependencyBuildersPair.getSecond();
    }

    EntityStorage entityStorage = WorkspaceModel.getInstance(project).getCurrentSnapshot();
    for (RootsChangeRescanningInfo change : changes) {
      if (change == RootsChangeRescanningInfo.NO_RESCAN_NEEDED || change == RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED) {
        continue;
      }
      if (change instanceof WorkspaceEventRescanningInfo) {
        builders.addAll(getBuildersOnWorkspaceChange(project, ((WorkspaceEventRescanningInfo)change).events, entityStorage));
      }
      else if (change instanceof WorkspaceEntitiesRootsChangedRescanningInfo) {
        List<EntityReference<WorkspaceEntity>> references = ((WorkspaceEntitiesRootsChangedRescanningInfo)change).references;
        List<@NotNull WorkspaceEntity> entities = ContainerUtil.mapNotNull(references, (ref) -> ref.resolve(entityStorage));
        builders.addAll(getBuildersOnWorkspaceEntitiesRootsChange(project, entities, entityStorage));
      }
      else if (change instanceof BuildableRootsChangeRescanningInfo) {
        builders.addAll(getBuildersOnBuildableChangeInfo((BuildableRootsChangeRescanningInfo)change));
      }
      else {
        LOG.warn("Unexpected change " + change.getClass() + " " + change + ", full reindex requested");
        runFullRescan(project, "Reindex on unexpected change in EntityIndexingServiceImpl");
        return;
      }
    }

    if (!builders.isEmpty()) {
      List<IndexableFilesIterator> mergedIterators =
        IndexableIteratorBuilders.INSTANCE.instantiateBuilders(builders, project, entityStorage);

      if (!mergedIterators.isEmpty()) {
        List<String> debugNames = ContainerUtil.map(mergedIterators, IndexableFilesIterator::getDebugName);
        LOG.debug("Accumulated iterators: " + debugNames);
        int maxNamesToLog = 10;
        String reasonMessage = "changes in: " + debugNames
          .stream()
          .limit(maxNamesToLog)
          .map(StringUtil::wrapWithDoubleQuote).collect(Collectors.joining(", "));
        if (debugNames.size() > maxNamesToLog) {
          reasonMessage += " and " + (debugNames.size() - maxNamesToLog) + " iterators more";
        }
        logRootChanges(project, false);
        new UnindexedFilesUpdater(project, mergedIterators, dependenciesStatusMark, reasonMessage).queue();
      }
    }
  }

  private static void runFullRescan(@NotNull Project project, @NotNull @NonNls String reason) {
    logRootChanges(project, true);
    new UnindexedFilesUpdater(project, reason).queue();
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
                                                   @NotNull Collection<? extends EntityChange<?>> events) {
    EntityStorage entityStorage = WorkspaceModel.getInstance(project).getCurrentSnapshot();
    List<IndexableIteratorBuilder> result = getBuildersOnWorkspaceChange(project, events, entityStorage);
    return IndexableIteratorBuilders.INSTANCE.instantiateBuilders(result, project, entityStorage);
  }

  private enum Change {
    Added, Replaced, Removed;

    static Change fromEntityChange(EntityChange<?> change) {
      if (change instanceof EntityChange.Added<?>) return Added;
      if (change instanceof EntityChange.Replaced<?>) return Replaced;
      if (change instanceof EntityChange.Removed<?>) return Removed;
      throw new IllegalStateException("Unexpected change " + change);
    }
  }

  @NotNull
  private static List<IndexableIteratorBuilder> getBuildersOnWorkspaceChange(@NotNull Project project,
                                                                             @NotNull Collection<? extends EntityChange<?>> events,
                                                                             @NotNull EntityStorage entityStorage) {
    List<IndexableIteratorBuilder> builders = new SmartList<>();
    WorkspaceIndexingRootsBuilder descriptionsBuilder = new WorkspaceIndexingRootsBuilder();
    for (EntityChange<? extends WorkspaceEntity> change : events) {
      collectIteratorBuildersOnChange(Change.fromEntityChange(change), change.getOldEntity(), change.getNewEntity(), project, builders,
                                      descriptionsBuilder, entityStorage);
    }
    builders.addAll(descriptionsBuilder.createBuilders(project));
    return builders;
  }

  private static <E extends WorkspaceEntity> void collectIteratorBuildersOnChange(@NotNull Change change,
                                                                                  @Nullable E oldEntity,
                                                                                  @Nullable E newEntity,
                                                                                  @NotNull Project project,
                                                                                  @NotNull Collection<? super IndexableIteratorBuilder> builders,
                                                                                  @NotNull WorkspaceIndexingRootsBuilder descriptionsBuilder,
                                                                                  @NotNull EntityStorage entityStorage) {
    LOG.assertTrue(newEntity != null || change == Change.Removed, "New entity " + newEntity + ", change " + change);
    LOG.assertTrue(oldEntity != null || change == Change.Added, "Old entity " + oldEntity + ", change " + change);

    //noinspection unchecked
    Class<? super E> entityClass = (Class<? super E>)Objects.requireNonNull(newEntity == null ? oldEntity : newEntity).getEntityInterface();

    if (IndexableFilesIndex.isEnabled()) {
      List<IndexableIteratorBuilder> newBuilders = new ArrayList<>();
      collectWFICIteratorsOnChange(change, oldEntity, newEntity, project, newBuilders, descriptionsBuilder, entityClass, false,
                                   entityStorage);
      builders.addAll(newBuilders);
    }
    else {
      List<IndexableIteratorBuilder> oldBuilders = new ArrayList<>();
      collectIEPIteratorsOnChange(change, oldEntity, newEntity, project, oldBuilders, descriptionsBuilder, entityClass, false,
                                  entityStorage);
      builders.addAll(oldBuilders);
    }
  }

  private static <E extends WorkspaceEntity> void collectIEPIteratorsOnChange(@NotNull Change change,
                                                                              @Nullable E oldEntity,
                                                                              @Nullable E newEntity,
                                                                              @NotNull Project project,
                                                                              @NotNull Collection<? super IndexableIteratorBuilder> builders,
                                                                              @NotNull WorkspaceIndexingRootsBuilder descriptionsBuilder,
                                                                              @NotNull Class<? super E> entityClass,
                                                                              boolean enforcedOnly,
                                                                              @NotNull EntityStorage entityStorage) {
    LOG.assertTrue(newEntity != null || change == Change.Removed, "New entity " + newEntity + ", change " + change);
    LOG.assertTrue(oldEntity != null || change == Change.Added, "Old entity " + oldEntity + ", change " + change);

    for (IndexableEntityProvider<?> uncheckedProvider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (entityClass == uncheckedProvider.getEntityClass() &&
          (!enforcedOnly || uncheckedProvider instanceof IndexableEntityProvider.Enforced<?>)) {
        //noinspection unchecked
        IndexableEntityProvider<E> provider = (IndexableEntityProvider<E>)uncheckedProvider;
        Collection<? extends IndexableIteratorBuilder> generated = switch (change) {
          case Added -> provider.getAddedEntityIteratorBuilders(newEntity, project);
          case Replaced -> provider.getReplacedEntityIteratorBuilders(oldEntity, newEntity, project);
          case Removed -> provider.getRemovedEntityIteratorBuilders(oldEntity, project);
        };
        builders.addAll(generated);
      }

      if (change == Change.Replaced && (!enforcedOnly || uncheckedProvider instanceof IndexableEntityProvider.Enforced<?>)) {
        for (IndexableEntityProvider.DependencyOnParent<? extends WorkspaceEntity> dependency : uncheckedProvider.getDependencies()) {
          if (entityClass == dependency.getParentClass()) {
            //noinspection unchecked
            builders.addAll(((IndexableEntityProvider.DependencyOnParent<E>)dependency).
                              getReplacedEntityIteratorBuilders(oldEntity, newEntity));
          }
        }
      }
    }

    if (!enforcedOnly) {
      collectWFICIteratorsOnChange(change, oldEntity, newEntity, project, builders, descriptionsBuilder, entityClass, true,
                                   entityStorage
      );
    }
  }

  private static <E extends WorkspaceEntity> void collectWFICIteratorsOnChange(@NotNull Change change,
                                                                               @Nullable E oldEntity,
                                                                               @Nullable E newEntity,
                                                                               @NotNull Project project,
                                                                               @NotNull Collection<? super IndexableIteratorBuilder> builders,
                                                                               @NotNull WorkspaceIndexingRootsBuilder descriptionsBuilder,
                                                                               @NotNull Class<? super E> entityClass,
                                                                               boolean customOnly,
                                                                               @NotNull EntityStorage entityStorage) {
    LOG.assertTrue(newEntity != null || change == Change.Removed, "New entity " + newEntity + ", change " + change);
    LOG.assertTrue(oldEntity != null || change == Change.Added, "Old entity " + oldEntity + ", change " + change);

    List<WorkspaceFileIndexContributor<?>> contributors =
      ((WorkspaceFileIndexImpl)WorkspaceFileIndex.getInstance(project)).getContributors();
    for (WorkspaceFileIndexContributor<?> uncheckedContributor : contributors) {
      if (uncheckedContributor.getStorageKind() != EntityStorageKind.MAIN) {
        continue;
      }
      if (customOnly && uncheckedContributor instanceof PlatformInternalWorkspaceFileIndexContributor) {
        continue;
      }
      if (entityClass == uncheckedContributor.getEntityClass()) {
        //noinspection unchecked
        WorkspaceFileIndexContributor<E> contributor = (WorkspaceFileIndexContributor<E>)uncheckedContributor;
        switch (change) {
          case Added -> descriptionsBuilder.registerAddedEntity(newEntity, contributor, entityStorage);
          case Replaced -> descriptionsBuilder.registerChangedEntity(oldEntity, newEntity, contributor, entityStorage);
          case Removed -> descriptionsBuilder.registerRemovedEntity(oldEntity, contributor, entityStorage);
        }
      }
      if (change == Change.Replaced) {
        handleDependencies(oldEntity, newEntity, descriptionsBuilder, entityClass, uncheckedContributor,
                           entityStorage);
      }
    }

    if (!customOnly) {
      collectIEPIteratorsOnChange(change, oldEntity, newEntity, project, builders, descriptionsBuilder, entityClass, true,
                                  entityStorage);
    }

    if (!customOnly && change != Change.Removed && isLibraryIgnoredByLibraryRootFileIndexContributor(newEntity)) {
      LibraryIndexableEntityProvider provider = IndexableEntityProvider.EP_NAME.findExtensionOrFail(LibraryIndexableEntityProvider.class);
      if (change == Change.Added) {
        builders.addAll(provider.getAddedEntityIteratorBuilders((LibraryEntity)newEntity, project));
      }
      else {
        builders.addAll(provider.getReplacedEntityIteratorBuilders((LibraryEntity)oldEntity, (LibraryEntity)newEntity, project));
      }
    }
  }

  private static <E extends WorkspaceEntity> boolean isLibraryIgnoredByLibraryRootFileIndexContributor(@NotNull E newEntity) {
    return newEntity instanceof LibraryEntity &&
           ((LibraryEntity)newEntity).getSymbolicId().getTableId() instanceof LibraryTableId.GlobalLibraryTableId;
  }

  private static <E extends WorkspaceEntity, C extends WorkspaceEntity> void handleDependencies(@NotNull E oldEntity,
                                                                                                @NotNull E newEntity,
                                                                                                @NotNull WorkspaceIndexingRootsBuilder descriptionsBuilder,
                                                                                                @NotNull Class<? super E> entityClass,
                                                                                                @NotNull WorkspaceFileIndexContributor<C> contributor,
                                                                                                @NotNull EntityStorage entityStorage) {
    for (DependencyDescription<C> dependency : contributor.getDependenciesOnOtherEntities()) {
      handleChildEntities(entityClass, oldEntity, newEntity, descriptionsBuilder, contributor, dependency,
                          entityStorage);
    }
  }

  private static <E extends WorkspaceEntity, C extends WorkspaceEntity> void handleChildEntities(@NotNull Class<? super E> entityClass,
                                                                                                 @NotNull E oldEntity,
                                                                                                 @NotNull E newEntity,
                                                                                                 @NotNull WorkspaceIndexingRootsBuilder descriptionsBuilder,
                                                                                                 @NotNull WorkspaceFileIndexContributor<C> contributor,
                                                                                                 @NotNull DependencyDescription<C> dependency,
                                                                                                 @NotNull EntityStorage entityStorage) {
    if (!(dependency instanceof DependencyDescription.OnParent) ||
        entityClass != ((DependencyDescription.OnParent<?, ?>)dependency).getParentClass()) {
      return;
    }
    List<C> oldElements = SequencesKt.toList(((DependencyDescription.OnParent<C, E>)dependency).getChildrenGetter().invoke(oldEntity));
    List<C> newElements =
      SequencesKt.toMutableList(((DependencyDescription.OnParent<C, E>)dependency).getChildrenGetter().invoke(newEntity));

    newElements.removeAll(oldElements);
    for (C element : newElements) {
      descriptionsBuilder.registerAddedEntity(element, contributor, entityStorage);
    }
  }

  private static Collection<? extends IndexableIteratorBuilder> getBuildersOnWorkspaceEntitiesRootsChange(@NotNull Project project,
                                                                                                          @NotNull List<? extends WorkspaceEntity> entities,
                                                                                                          @NotNull EntityStorage entityStorage) {
    List<IndexableIteratorBuilder> builders = new SmartList<>();

    WorkspaceIndexingRootsBuilder descriptionsBuilder = new WorkspaceIndexingRootsBuilder();
    for (WorkspaceEntity entity : entities) {
      collectIteratorBuildersOnChange(Change.Added, null, entity, project, builders, descriptionsBuilder, entityStorage);
    }
    builders.addAll(descriptionsBuilder.createBuilders(project));
    return builders;
  }

  @NotNull
  private static Collection<? extends IndexableIteratorBuilder> getBuildersOnBuildableChangeInfo(@NotNull BuildableRootsChangeRescanningInfo buildableInfo) {
    BuildableRootsChangeRescanningInfoImpl info = (BuildableRootsChangeRescanningInfoImpl)buildableInfo;
    List<IndexableIteratorBuilder> builders = new SmartList<>();
    IndexableIteratorBuilders instance = IndexableIteratorBuilders.INSTANCE;
    for (ModuleId moduleId : info.getModules()) {
      builders.addAll(instance.forModuleContent(moduleId));
    }
    if (info.hasInheritedSdk()) {
      builders.addAll(instance.forInheritedSdk());
    }
    for (Pair<String, String> sdk : info.getSdks()) {
      builders.addAll(instance.forSdk(sdk.getFirst(), sdk.getSecond()));
    }
    for (LibraryId library : info.getLibraries()) {
      builders.addAll(instance.forLibraryEntity(library, true));
    }
    return builders;
  }

  @Override
  @NotNull
  public BuildableRootsChangeRescanningInfo createBuildableInfo() {
    return new BuildableRootsChangeRescanningInfoImpl();
  }

  @Override
  public @NotNull RootsChangeRescanningInfo createWorkspaceChangedEventInfo(@NotNull List<EntityChange<?>> changes) {
    return new WorkspaceEventRescanningInfo(changes);
  }

  @NotNull
  @Override
  public RootsChangeRescanningInfo createWorkspaceEntitiesRootsChangedInfo(@NotNull List<EntityReference<WorkspaceEntity>> references) {
    return new WorkspaceEntitiesRootsChangedRescanningInfo(references);
  }

  @Override
  public boolean isFromWorkspaceOnly(@NotNull List<? extends RootsChangeRescanningInfo> indexingInfos) {
    if (indexingInfos.isEmpty()) return false;
    for (RootsChangeRescanningInfo info : indexingInfos) {
      if (!(info instanceof WorkspaceEventRescanningInfo)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean shouldCauseRescan(@NotNull WorkspaceEntity entity, @NotNull Project project) {
    return tracker.shouldRescan(entity, project);
  }

  private static class WorkspaceEventRescanningInfo implements RootsChangeRescanningInfo {
    @NotNull
    private final List<EntityChange<?>> events;

    private WorkspaceEventRescanningInfo(@NotNull List<EntityChange<?>> events) {
      this.events = events;
    }
  }

  private static class WorkspaceEntitiesRootsChangedRescanningInfo implements RootsChangeRescanningInfo {
    @NotNull
    private final List<EntityReference<WorkspaceEntity>> references;

    private WorkspaceEntitiesRootsChangedRescanningInfo(@NotNull List<EntityReference<WorkspaceEntity>> entities) {
      this.references = entities;
    }
  }
}