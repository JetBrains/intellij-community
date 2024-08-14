// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.jps.entities.*;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.BuildableRootsChangeRescanningInfoImpl.BuiltRescanningInfo;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService.StatusMark;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.WorkspaceIndexingRootsBuilder;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.util.indexing.roots.builders.IndexableSetContributorFilesIteratorBuilder;
import com.intellij.util.indexing.roots.builders.SyntheticLibraryIteratorBuilder;
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription;
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import kotlin.Pair;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.indexing.UnindexedFilesScannerStartupKt.invalidateProjectFilterIfFirstScanningNotRequested;

final class EntityIndexingServiceImpl implements EntityIndexingServiceEx {
  private static final Logger LOG = Logger.getInstance(EntityIndexingServiceImpl.class);
  private static final RootChangesLogger ROOT_CHANGES_LOGGER = new RootChangesLogger();
  private final @NotNull CustomEntitiesCausingReindexTracker tracker = new CustomEntitiesCausingReindexTracker();

  @Override
  public void indexChanges(@NotNull Project project, @NotNull List<? extends RootsChangeRescanningInfo> changes) {
    if (!(FileBasedIndex.getInstance() instanceof FileBasedIndexImpl)) return;
    if (LightEdit.owns(project)) return;
    if (invalidateProjectFilterIfFirstScanningNotRequested(project)) return;

    if (ModalityState.defaultModalityState() == ModalityState.any()) {
      LOG.error("Unexpected modality: should not be ANY. Replace with NON_MODAL (130820241337)");
    }

    if (changes.isEmpty()) {
      runFullRescan(project, "Project roots have changed");
    }
    if (DumbServiceImpl.isSynchronousTaskExecution()) {
      doIndexChanges(project, changes);
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        if (ModalityState.defaultModalityState() == ModalityState.any()) {
          LOG.error("Unexpected modality: should not be ANY. Replace with NON_MODAL (140820241138)");
        }
        doIndexChanges(project, changes);
      });
    }
  }

  private static void doIndexChanges(@NotNull Project project, @NotNull List<? extends RootsChangeRescanningInfo> changes) {
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
        List<EntityPointer<WorkspaceEntity>> pointers = ((WorkspaceEntitiesRootsChangedRescanningInfo)change).pointers;
        List<@NotNull WorkspaceEntity> entities = ContainerUtil.mapNotNull(pointers, (ref) -> ref.resolve(entityStorage));
        builders.addAll(getBuildersOnWorkspaceEntitiesRootsChange(project, entities, entityStorage));
      }
      else if (change instanceof BuiltRescanningInfo) {
        builders.addAll(getBuildersOnBuildableChangeInfo((BuiltRescanningInfo)change, project, entityStorage));
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
        new UnindexedFilesScanner(project, mergedIterators, dependenciesStatusMark, reasonMessage).queue();
      }
    }
  }

  private static void runFullRescan(@NotNull Project project, @NotNull @NonNls String reason) {
    logRootChanges(project, true);
    new UnindexedFilesScanner(project, reason).queue();
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
  static @NotNull List<IndexableFilesIterator> getIterators(@NotNull Project project,
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

  private static @NotNull List<IndexableIteratorBuilder> getBuildersOnWorkspaceChange(@NotNull Project project,
                                                                                      @NotNull Collection<? extends EntityChange<?>> events,
                                                                                      @NotNull EntityStorage entityStorage) {
    List<IndexableIteratorBuilder> builders = new SmartList<>();
    WorkspaceIndexingRootsBuilder descriptionsBuilder = new WorkspaceIndexingRootsBuilder(false);
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

    List<IndexableIteratorBuilder> newBuilders = new ArrayList<>();
    collectWFICIteratorsOnChange(change, oldEntity, newEntity, project, newBuilders, descriptionsBuilder, entityClass,
                                 entityStorage);
    builders.addAll(newBuilders);
  }

  private static <E extends WorkspaceEntity> void collectIEPIteratorsOnChange(@NotNull Change change,
                                                                              @Nullable E oldEntity,
                                                                              @Nullable E newEntity,
                                                                              @NotNull Project project,
                                                                              @NotNull Collection<? super IndexableIteratorBuilder> builders,
                                                                              @NotNull Class<? super E> entityClass) {
    LOG.assertTrue(newEntity != null || change == Change.Removed, "New entity " + newEntity + ", change " + change);
    LOG.assertTrue(oldEntity != null || change == Change.Added, "Old entity " + oldEntity + ", change " + change);

    for (IndexableEntityProvider<?> uncheckedProvider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
      if (entityClass == uncheckedProvider.getEntityClass() && uncheckedProvider instanceof IndexableEntityProvider.Enforced<?>) {
        //noinspection unchecked
        IndexableEntityProvider<E> provider = (IndexableEntityProvider<E>)uncheckedProvider;
        Collection<? extends IndexableIteratorBuilder> generated = switch (change) {
          case Added -> provider.getAddedEntityIteratorBuilders(newEntity, project);
          case Replaced -> provider.getReplacedEntityIteratorBuilders(oldEntity, newEntity, project);
          case Removed -> provider.getRemovedEntityIteratorBuilders(oldEntity, project);
        };
        builders.addAll(generated);
      }

      if (change == Change.Replaced && uncheckedProvider instanceof IndexableEntityProvider.Enforced<?>) {
        for (IndexableEntityProvider.DependencyOnParent<? extends WorkspaceEntity> dependency : uncheckedProvider.getDependencies()) {
          if (entityClass == dependency.getParentClass()) {
            //noinspection unchecked
            builders.addAll(((IndexableEntityProvider.DependencyOnParent<E>)dependency).
                              getReplacedEntityIteratorBuilders(oldEntity, newEntity));
          }
        }
      }
    }
  }

  private static <E extends WorkspaceEntity> void collectWFICIteratorsOnChange(@NotNull Change change,
                                                                               @Nullable E oldEntity,
                                                                               @Nullable E newEntity,
                                                                               @NotNull Project project,
                                                                               @NotNull Collection<? super IndexableIteratorBuilder> builders,
                                                                               @NotNull WorkspaceIndexingRootsBuilder descriptionsBuilder,
                                                                               @NotNull Class<? super E> entityClass,
                                                                               @NotNull EntityStorage entityStorage) {
    LOG.assertTrue(newEntity != null || change == Change.Removed, "New entity " + newEntity + ", change " + change);
    LOG.assertTrue(oldEntity != null || change == Change.Added, "Old entity " + oldEntity + ", change " + change);

    List<WorkspaceFileIndexContributor<?>> contributors = WorkspaceFileIndexImpl.Companion.getEP_NAME().getExtensionList();
    for (WorkspaceFileIndexContributor<?> uncheckedContributor : contributors) {
      if (uncheckedContributor.getStorageKind() != EntityStorageKind.MAIN) {
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

    collectIEPIteratorsOnChange(change, oldEntity, newEntity, project, builders, entityClass);

    if (change != Change.Removed && isLibraryIgnoredByLibraryRootFileIndexContributor(newEntity)) {
      if (change == Change.Added) {
        // Sure, we are interested only in libraries used in the project, but in case a registered library is downloaded,
        // no change in dependencies happens, only Added event on LibraryEntity.
        // For debug see com.intellij.roots.libraries.LibraryTest
        builders.addAll(IndexableIteratorBuilders.INSTANCE.forLibraryEntity(((LibraryEntity)newEntity).getSymbolicId(), false));
      }
      else if (change == Change.Replaced && hasSomethingToIndex((LibraryEntity)oldEntity, (LibraryEntity)newEntity)) {
        builders.addAll(IndexableIteratorBuilders.INSTANCE.forLibraryEntity(((LibraryEntity)newEntity).getSymbolicId(), false));
      }
    }
  }

  private static boolean hasSomethingToIndex(@NotNull LibraryEntity oldEntity, @NotNull LibraryEntity newEntity) {
    if (newEntity.getRoots().size() > oldEntity.getRoots().size()) return true;
    if (oldEntity.getExcludedRoots().size() > newEntity.getExcludedRoots().size()) return true;
    List<LibraryRoot> oldEntityRoots = oldEntity.getRoots();
    for (LibraryRoot root : newEntity.getRoots()) {
      if (!oldEntityRoots.contains(root)) return true;
    }
    List<ExcludeUrlEntity> newEntityExcludedRoots = newEntity.getExcludedRoots();
    for (ExcludeUrlEntity excludedRoot : oldEntity.getExcludedRoots()) {
      if (!newEntityExcludedRoots.contains(excludedRoot.getUrl())) return true;
    }
    return false;
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
                                                                                                          @NotNull Collection<? extends WorkspaceEntity> entities,
                                                                                                          @NotNull EntityStorage entityStorage) {
    if (entities.isEmpty()) return Collections.emptyList();
    List<IndexableIteratorBuilder> builders = new SmartList<>();

    WorkspaceIndexingRootsBuilder descriptionsBuilder = new WorkspaceIndexingRootsBuilder(false);
    for (WorkspaceEntity entity : entities) {
      collectIteratorBuildersOnChange(Change.Added, null, entity, project, builders, descriptionsBuilder, entityStorage);
    }
    builders.addAll(descriptionsBuilder.createBuilders(project));
    return builders;
  }

  private static @NotNull Collection<? extends IndexableIteratorBuilder> getBuildersOnBuildableChangeInfo(@NotNull BuiltRescanningInfo info,
                                                                                                          @NotNull Project project,
                                                                                                          @NotNull EntityStorage entityStorage) {
    List<IndexableIteratorBuilder> builders = new SmartList<>();
    IndexableIteratorBuilders instance = IndexableIteratorBuilders.INSTANCE;
    for (ModuleId moduleId : info.modules()) {
      builders.addAll(instance.forModuleContent(moduleId));
    }
    if (info.hasInheritedSdk()) {
      builders.addAll(instance.forInheritedSdk());
    }
    for (Pair<String, String> sdk : info.sdks()) {
      builders.addAll(instance.forSdk(sdk.getFirst(), sdk.getSecond()));
    }
    for (LibraryId library : info.libraries()) {
      builders.addAll(instance.forLibraryEntity(library, true));
    }
    builders.addAll(getBuildersOnWorkspaceEntitiesRootsChange(project, info.entities(), entityStorage));
    return builders;
  }

  @Override
  public @NotNull BuildableRootsChangeRescanningInfo createBuildableInfoBuilder() {
    return new BuildableRootsChangeRescanningInfoImpl();
  }

  @Override
  public @NotNull RootsChangeRescanningInfo createWorkspaceChangedEventInfo(@NotNull List<EntityChange<?>> changes) {
    return new WorkspaceEventRescanningInfo(changes);
  }

  @Override
  public @NotNull RootsChangeRescanningInfo createWorkspaceEntitiesRootsChangedInfo(@NotNull List<EntityPointer<WorkspaceEntity>> pointers) {
    return new WorkspaceEntitiesRootsChangedRescanningInfo(pointers);
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
  public boolean shouldCauseRescan(@Nullable WorkspaceEntity oldEntity, @Nullable WorkspaceEntity newEntity, @NotNull Project project) {
    return tracker.shouldRescan(oldEntity, newEntity, project);
  }

  private static final class WorkspaceEventRescanningInfo implements RootsChangeRescanningInfo {
    private final @NotNull List<EntityChange<?>> events;

    private WorkspaceEventRescanningInfo(@NotNull List<EntityChange<?>> events) {
      this.events = events;
    }
  }

  private static final class WorkspaceEntitiesRootsChangedRescanningInfo implements RootsChangeRescanningInfo {
    private final @NotNull List<EntityPointer<WorkspaceEntity>> pointers;

    private WorkspaceEntitiesRootsChangedRescanningInfo(@NotNull List<EntityPointer<WorkspaceEntity>> entities) {
      this.pointers = entities;
    }
  }

  @Override
  public @NotNull Collection<IndexableFilesIterator> createIteratorsForOrigins(@NotNull Project project,
                                                                               @NotNull EntityStorage entityStorage,
                                                                               @NotNull Collection<EntityPointer<?>> entityPointers,
                                                                               @NotNull Collection<Sdk> sdks,
                                                                               @NotNull Collection<LibraryId> libraryIds,
                                                                               @NotNull Collection<VirtualFile> filesFromAdditionalLibraryRootsProviders,
                                                                               @NotNull Collection<VirtualFile> filesFromIndexableSetContributors) {
    List<WorkspaceEntity> entities = ContainerUtil.mapNotNull(entityPointers, (ref) -> ref.resolve(entityStorage));
    List<IndexableIteratorBuilder> builders = new ArrayList<>(getBuildersOnWorkspaceEntitiesRootsChange(project, entities, entityStorage));

    for (Sdk sdk : sdks) {
      builders.addAll(IndexableIteratorBuilders.INSTANCE.forSdk(sdk.getName(), sdk.getSdkType().getName()));
    }
    for (LibraryId id : libraryIds) {
      builders.addAll(IndexableIteratorBuilders.INSTANCE.forLibraryEntity(id, true));
    }

    if (!filesFromAdditionalLibraryRootsProviders.isEmpty()) {
      List<VirtualFile> roots = new ArrayList<>(filesFromAdditionalLibraryRootsProviders);
      for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
        for (SyntheticLibrary library : provider.getAdditionalProjectLibraries(project)) {
          boolean removed = roots.removeIf(file -> library.contains(file));
          if (removed) {
            String name = library instanceof ItemPresentation ? ((ItemPresentation)library).getPresentableText() : null;
            builders.add(new SyntheticLibraryIteratorBuilder(library, name, library.getAllRoots()));
          }
          if (roots.isEmpty()) {
            break;
          }
        }
      }
      if (!roots.isEmpty()) {
        LOG.error("Failed fo find any SyntheticLibrary roots for " + StringUtil.join(roots, "\n"));
      }
    }

    if (!filesFromIndexableSetContributors.isEmpty()) {
      List<VirtualFile> roots = new ArrayList<>(filesFromIndexableSetContributors);
      for (IndexableSetContributor contributor : IndexableSetContributor.EP_NAME.getExtensionList()) {
        Set<VirtualFile> applicationRoots = contributor.getAdditionalRootsToIndex();
        boolean removedApp = roots.removeIf(file -> VfsUtilCore.isUnder(file, applicationRoots));
        if (removedApp) {
          builders.add(
            new IndexableSetContributorFilesIteratorBuilder(null, contributor.getDebugName(), applicationRoots, false, contributor));
        }
        Set<VirtualFile> projectRoots = contributor.getAdditionalProjectRootsToIndex(project);
        boolean removedProject = roots.removeIf(file -> VfsUtilCore.isUnder(file, projectRoots));
        if (removedProject) {
          builders.add(new IndexableSetContributorFilesIteratorBuilder(null, contributor.getDebugName(), projectRoots, true, contributor));
        }
        if (roots.isEmpty()) {
          break;
        }
      }
    }

    return IndexableIteratorBuilders.INSTANCE.instantiateBuilders(builders, project, entityStorage);
  }
}