// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.EntityIndexingServiceEx;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.roots.kind.IndexableSetSelfDependentOrigin;
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin;
import com.intellij.util.indexing.roots.kind.SdkOrigin;
import com.intellij.util.indexing.roots.origin.IndexableSetContributorSelfDependentOriginImpl;
import com.intellij.util.indexing.roots.origin.ModuleRootSelfDependentOriginImpl;
import com.intellij.util.indexing.roots.origin.SdkSelfDependentOriginImpl;
import com.intellij.util.indexing.roots.origin.SyntheticLibrarySelfDependentOriginImpl;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.storage.*;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.SourceRootEntity;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;

@ApiStatus.Experimental
@ApiStatus.Internal
public class IndexableFilesIndex {
  private static final Logger LOG = Logger.getInstance(IndexableFilesIndex.class);
  @VisibleForTesting
  public static final Key<Boolean> ENABLE_IN_TESTS = new Key<>("enable.IndexableFilesIndex");
  @NotNull
  private static final FileTypeRegistry ourFileTypes = FileTypeRegistry.getInstance();
  @NotNull
  private final Project project;
  @NotNull
  private WorkspaceModelStatus workspaceModelStatus;
  @Nullable
  private NonWorkspaceModelSnapshot nonWorkspaceSnapshot;
  @Nullable
  private ResultingSnapshot snapshot;

  public static boolean shouldBeUsed() {
    return (Registry.is("indexing.use.indexable.files.index") ||
            (ApplicationManager.getApplication().isUnitTestMode() && TestModeFlags.is(ENABLE_IN_TESTS))) &&
           DefaultProjectIndexableFilesContributor.Companion.indexProjectBasedOnIndexableEntityProviders();
  }

  @NotNull
  public static IndexableFilesIndex getInstance(@NotNull Project project) {
    LOG.assertTrue(shouldBeUsed());
    return project.getService(IndexableFilesIndex.class);
  }

  public IndexableFilesIndex(@NotNull Project project) {
    this.project = project;
    workspaceModelStatus = new WorkspaceModelStatus(project);
    regenerateResultingSnapshot();
  }

  private void resetSnapshots() {
    nonWorkspaceSnapshot = null;
    snapshot = null;
  }

  private void regenerateResultingSnapshot() {
    if (nonWorkspaceSnapshot != null) {
      snapshot = new ResultingSnapshot(workspaceModelStatus, nonWorkspaceSnapshot);
    }
  }

  public boolean shouldBeIndexed(@NotNull VirtualFile file) {
    return getOrigin(file) != null;
  }

  @Nullable
  public IndexableSetSelfDependentOrigin getOrigin(@NotNull VirtualFile file) {
    VirtualFile currentFile = file;
    ensureSnapshotGenerated();
    boolean isExcludedFromContent = false;
    while (currentFile != null) {
      Collection<IndexableSetSelfDependentOrigin> origins = snapshot.roots.get(currentFile);
      for (IndexableSetSelfDependentOrigin origin : origins) {
        // situation with content/source roots higher in hierarchy is ignored when a file's already excluded from a lower root
        if (origin instanceof ModuleRootOrigin) {
          if (!isExcludedFromContent) {
            if (!origin.isExcluded(file)) {
              return origin;
            }
            else {
              isExcludedFromContent = true;
            }
          }
        }
        else if (!origin.isExcluded(file)) {
          return origin;
        }
      }
      if (ourFileTypes.isFileIgnored(currentFile)) {
        return null;
      }
      currentFile = currentFile.getParent();
    }
    return null;
  }

  private void ensureSnapshotGenerated() {
    if (nonWorkspaceSnapshot == null) {
      nonWorkspaceSnapshot = NonWorkspaceModelSnapshot.buildSnapshot(project);
      regenerateResultingSnapshot();
    }
  }

  private static class ResultingSnapshot {
    private final ImmutableSetMultimap<VirtualFile, IndexableSetSelfDependentOrigin> roots;

    ResultingSnapshot(@NotNull WorkspaceModelStatus status,
                      @NotNull NonWorkspaceModelSnapshot snapshot) {
      Collection<IndexableSetSelfDependentOrigin> origins = new ArrayList<>();
      Set<VirtualFile> excludedModuleFilesFromPolicies = new HashSet<>(snapshot.excludedFilesFromPolicies);
      excludedModuleFilesFromPolicies.addAll(
        ContainerUtil.mapNotNull(snapshot.excludedModuleFilesFromPolicies, pointer -> pointer.getFile()));
      for (Map.Entry<WorkspaceEntity, Collection<IndexableSetSelfDependentOrigin>> entry : status.entitiesToOrigins.entrySet()) {
        for (IndexableSetSelfDependentOrigin origin : entry.getValue()) {
          if (origin instanceof SdkSelfDependentOriginImpl) {
            List<VirtualFile> excludedRootsForSdk = new ArrayList<>();
            for (Function<Sdk, List<VirtualFile>> exclusionFunction : snapshot.sdkExclusionFunctions) {
              List<VirtualFile> roots = exclusionFunction.fun(((SdkOrigin)origin).getSdk());
              if (roots != null && !roots.isEmpty()) {
                excludedRootsForSdk.addAll(roots);
              }
            }
            if (!excludedRootsForSdk.isEmpty()) {
              origins.add(((SdkSelfDependentOriginImpl)origin).copyWithAdditionalExcludedFiles(excludedRootsForSdk));
              continue;
            }
          }
          else if (origin instanceof ModuleRootSelfDependentOriginImpl && !excludedModuleFilesFromPolicies.isEmpty()) {
            origins.add(((ModuleRootSelfDependentOriginImpl)origin).copyWithAdditionalExcludedFiles(excludedModuleFilesFromPolicies));
            continue;
          }
          origins.add(origin);
        }
      }
      origins.addAll(snapshot.indexableSetOrigins);
      origins.addAll(snapshot.syntheticLibrariesOrigins);

      ImmutableSetMultimap.Builder<VirtualFile, IndexableSetSelfDependentOrigin> builder = new ImmutableSetMultimap.Builder<>();
      for (IndexableSetSelfDependentOrigin origin : origins) {
        for (VirtualFile root : origin.getIterationRoots()) {
          builder.put(root, origin);
        }
      }
      roots = builder.build();
    }
  }

  private static class WorkspaceModelStatus {
    private final ImmutableMap<WorkspaceEntity, Collection<IndexableSetSelfDependentOrigin>> entitiesToOrigins;

    private WorkspaceModelStatus(@NotNull ImmutableSetMultimap.Builder<WorkspaceEntity, IndexableSetSelfDependentOrigin> builder) {
      this.entitiesToOrigins = builder.build().asMap();
    }

    private WorkspaceModelStatus(@NotNull Project project) {
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
    private WorkspaceModelStatus createChangedIfNeeded(@NotNull VersionedStorageChange storageChange, @NotNull Project project) {
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
      return new WorkspaceModelStatus(copy);
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
    private WorkspaceModelStatus createWithRefreshedEntitiesIfNeeded(@NotNull List<? extends WorkspaceEntity> entities,
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
      return new WorkspaceModelStatus(result);
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

  private static class NonWorkspaceModelSnapshot {
    private final Collection<IndexableSetContributorSelfDependentOriginImpl> indexableSetOrigins;
    private final Collection<SyntheticLibrarySelfDependentOriginImpl> syntheticLibrariesOrigins;
    private final Set<VirtualFile> excludedFilesFromPolicies;
    private final Set<VirtualFilePointer> excludedModuleFilesFromPolicies;
    private final List<@NotNull Function<Sdk, List<VirtualFile>>> sdkExclusionFunctions;

    private NonWorkspaceModelSnapshot(Collection<IndexableSetContributorSelfDependentOriginImpl> indexableSetOrigins,
                                      Collection<SyntheticLibrarySelfDependentOriginImpl> syntheticLibrariesOrigins,
                                      Set<VirtualFile> excludedFilesFromPolicies,
                                      Set<VirtualFilePointer> excludedModuleFilesFromPolicies,
                                      List<@NotNull Function<Sdk, List<VirtualFile>>> sdkExclusionFunctions) {
      this.indexableSetOrigins = List.copyOf(indexableSetOrigins);
      this.syntheticLibrariesOrigins = List.copyOf(syntheticLibrariesOrigins);
      this.excludedFilesFromPolicies = Set.copyOf(excludedFilesFromPolicies);
      this.excludedModuleFilesFromPolicies = Set.copyOf(excludedModuleFilesFromPolicies);
      this.sdkExclusionFunctions = List.copyOf(sdkExclusionFunctions);
    }

    @NotNull
    private static NonWorkspaceModelSnapshot buildSnapshot(@NotNull Project project) {
      Collection<SyntheticLibrarySelfDependentOriginImpl> syntheticLibrariesOrigins = new ArrayList<>();
      for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
        Collection<SyntheticLibrary> libraries = provider.getAdditionalProjectLibraries(project);
        for (SyntheticLibrary library : libraries) {
          SyntheticLibrarySelfDependentOriginImpl origin = new SyntheticLibrarySelfDependentOriginImpl(library,
                                                                                                       library.getAllRoots(),
                                                                                                       library.getExcludedRoots(),
                                                                                                       library.getUnitedExcludeCondition());
          syntheticLibrariesOrigins.add(origin);
        }
      }
      Collection<IndexableSetContributorSelfDependentOriginImpl> indexableSetOrigins = new ArrayList<>();
      for (IndexableSetContributor contributor : IndexableSetContributor.EP_NAME.getExtensionList()) {
        Set<VirtualFile> roots = contributor.getAdditionalRootsToIndex();
        IndexableSetContributorSelfDependentOriginImpl origin = new IndexableSetContributorSelfDependentOriginImpl(contributor, roots);
        indexableSetOrigins.add(origin);

        Set<VirtualFile> projectRoots = contributor.getAdditionalProjectRootsToIndex(project);
        indexableSetOrigins.add(new IndexableSetContributorSelfDependentOriginImpl(contributor, projectRoots));
      }

      Set<VirtualFile> excludedFilesFromPolicies = new HashSet<>();
      Set<VirtualFilePointer> excludedModuleFilesFromPolicies = new HashSet<>();
      List<@NotNull Function<Sdk, List<VirtualFile>>> sdkExclusionFunctions = new ArrayList<>();
      List<ModuleRootModel> modules =
        ContainerUtil.map(ModuleManager.getInstance(project).getModules(), module -> ModuleRootManager.getInstance(module));
      for (DirectoryIndexExcludePolicy policy : DirectoryIndexExcludePolicy.getExtensions(project)) {
        VirtualFileManager fileManager = VirtualFileManager.getInstance();
        String[] urls = policy.getExcludeUrlsForProject();
        excludedFilesFromPolicies.addAll(ContainerUtil.mapNotNull(urls, url -> fileManager.findFileByUrl(url)));

        Function<Sdk, List<VirtualFile>> strategy = policy.getExcludeSdkRootsStrategy();
        if (strategy != null) {
          sdkExclusionFunctions.add(strategy);
        }

        for (ModuleRootModel model : modules) {
          ContainerUtil.addAll(excludedModuleFilesFromPolicies, policy.getExcludeRootsForModule(model));
        }
      }
      return new NonWorkspaceModelSnapshot(indexableSetOrigins, syntheticLibrariesOrigins, excludedFilesFromPolicies,
                                           excludedModuleFilesFromPolicies, sdkExclusionFunctions);
    }


    @Nullable
    public NonWorkspaceModelSnapshot rebuildExcludedFromModuleRootsIfNeeded(@NotNull VersionedStorageChange storageChange,
                                                                            @NotNull Project project) {
      boolean shouldRebuild = ContainerUtil.exists(storageChange.getChanges(ModuleEntity.class),
                                                   change -> change instanceof EntityChange.Added<ModuleEntity> ||
                                                             change instanceof EntityChange.Removed<?>);
      if (shouldRebuild) {
        Set<VirtualFilePointer> excludedModuleFilesFromPolicies = new HashSet<>();
        List<ModuleRootModel> modules =
          ContainerUtil.map(ModuleManager.getInstance(project).getModules(), module -> ModuleRootManager.getInstance(module));
        for (DirectoryIndexExcludePolicy policy : DirectoryIndexExcludePolicy.getExtensions(project)) {
          for (ModuleRootModel model : modules) {
            ContainerUtil.addAll(excludedModuleFilesFromPolicies, policy.getExcludeRootsForModule(model));
          }
        }
        return new NonWorkspaceModelSnapshot(indexableSetOrigins, syntheticLibrariesOrigins, excludedFilesFromPolicies,
                                             excludedModuleFilesFromPolicies, sdkExclusionFunctions);
      }
      return null;
    }
  }

  public void workspaceModelChanged(@NotNull VersionedStorageChange storageChange) {
    @Nullable WorkspaceModelStatus changed = workspaceModelStatus.createChangedIfNeeded(storageChange, project);
    if (changed != null) {
      workspaceModelStatus = changed;
      snapshot = null;
    }
    if (nonWorkspaceSnapshot != null) {
      NonWorkspaceModelSnapshot newNonWorkspaceSnapshot =
        nonWorkspaceSnapshot.rebuildExcludedFromModuleRootsIfNeeded(storageChange, project);
      if (newNonWorkspaceSnapshot != null) {
        nonWorkspaceSnapshot = newNonWorkspaceSnapshot;
        snapshot = null;
      }
    }
  }

  public void beforeRootsChanged() {
    resetSnapshots();
  }

  public void afterRootsChanged(boolean fileTypes,
                                @NotNull List<? extends RootsChangeRescanningInfo> indexingInfos,
                                boolean isFromWorkspaceOnly) {
    if (fileTypes) {
      LOG.assertTrue(indexingInfos.isEmpty(), "File type root change event shouldn't have indexingInfos");
      LOG.assertTrue(!isFromWorkspaceOnly, "File type root change event has nothing to do with Workspace Model");
      resetSnapshots();
      return;
    }
    if (isFromWorkspaceOnly) return;
    List<WorkspaceEntity> entityWithChangedRoots = EntityIndexingServiceEx.getInstanceEx().getEntitiesWithChangedRoots(indexingInfos);
    WorkspaceModelStatus refreshed = workspaceModelStatus.createWithRefreshedEntitiesIfNeeded(entityWithChangedRoots, project);
    if (refreshed != null) {
      workspaceModelStatus = refreshed;
      snapshot = null;
    }
  }
}