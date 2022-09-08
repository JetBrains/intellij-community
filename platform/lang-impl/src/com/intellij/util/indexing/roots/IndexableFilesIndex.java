// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

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
import org.jetbrains.annotations.*;

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
  private final WorkspaceModelStatus workspaceModelStatus;
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
      snapshot = createResultingSnapshot(workspaceModelStatus, nonWorkspaceSnapshot);
    }
  }

  private static ResultingSnapshot createResultingSnapshot(@NotNull WorkspaceModelStatus status,
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
    return new ResultingSnapshot(origins);
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
    private final MultiMap<VirtualFile, IndexableSetSelfDependentOrigin> roots = MultiMap.createSet();

    ResultingSnapshot(Collection<IndexableSetSelfDependentOrigin> origins) {
      for (IndexableSetSelfDependentOrigin origin : origins) {
        for (VirtualFile root : origin.getIterationRoots()) {
          roots.putValue(root, origin);
        }
      }
    }
  }

  private static class WorkspaceModelStatus {
    private final MultiMap<WorkspaceEntity, IndexableSetSelfDependentOrigin> entitiesToOrigins = MultiMap.createSet();

    private WorkspaceModelStatus(@NotNull Project project) {
      rebuild(project);
    }

    private void rebuild(@NotNull Project project) {
      entitiesToOrigins.clear();
      EntityStorage storage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
      for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
        if (!(provider instanceof IndexableEntityProvider.ExistingEx<?>)) {
          continue;
        }
        handleProvider((IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>)provider, storage, project);
      }
    }

    private boolean changed(@NotNull VersionedStorageChange storageChange, @NotNull Project project) {
      EntityStorageSnapshot storage = storageChange.getStorageAfter();
      boolean changed = false;
      for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
        if (provider instanceof IndexableEntityProvider.ExistingEx<?>) {
          changed = changed || handleWorkspaceModelChange(storageChange,
                                                          (IndexableEntityProvider.ExistingEx<? extends WorkspaceEntity>)provider,
                                                          storage, project);
        }
      }
      return changed;
    }

    private <E extends WorkspaceEntity> boolean handleWorkspaceModelChange(@NotNull VersionedStorageChange storageChange,
                                                                           @NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                           @NotNull EntityStorageSnapshot storage,
                                                                           @NotNull Project project) {
      List<EntityChange<E>> changes = storageChange.getChanges(provider.getEntityClass());
      for (EntityChange<E> change : changes) {
        if (change instanceof EntityChange.Added<E>) {
          E entity = ((EntityChange.Added<E>)change).getEntity();
          addOrigins(entity, provider, storage, project);
        }
        else if (change instanceof EntityChange.Replaced<E>) {
          E oldEntity = Objects.requireNonNull(change.getOldEntity());
          removeOrigins(oldEntity, provider, storage, project);

          E newEntity = ((EntityChange.Replaced<E>)change).getNewEntity();
          addOrigins(newEntity, provider, storage, project);
        }
        else if (change instanceof EntityChange.Removed<E>) {
          E entity = ((EntityChange.Removed<E>)change).getEntity();
          removeOrigins(entity, provider, storage, project);
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
              removeOrigins(sourceRootEntity, sourceRootProvider, storage, project);
            }

            ContentRootEntity newEntity = ((EntityChange.Replaced<ContentRootEntity>)change).getNewEntity();
            for (SourceRootEntity sourceRootEntity : newEntity.getSourceRoots()) {
              addOrigins(sourceRootEntity, sourceRootProvider, storage, project);
            }
          }
        }
      }
      return !changes.isEmpty();
    }

    private <E extends WorkspaceEntity> void handleProvider(@NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                            @NotNull EntityStorage storage,
                                                            @NotNull Project project) {
      Class<E> aClass = provider.getEntityClass();
      for (E entity : SequencesKt.asIterable(storage.entities(aClass))) {
        addOrigins(entity, provider, storage, project);
      }
    }

    private <E extends WorkspaceEntity> void addOrigins(@NotNull E entity,
                                                        @NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                        @NotNull EntityStorage storage,
                                                        @NotNull Project project) {
      Collection<? extends IndexableSetSelfDependentOrigin> origins = provider.getExistingEntityIteratorOrigins(entity, storage, project);
      if (!origins.isEmpty()) {
        entitiesToOrigins.putValues(entity, origins);
      }
    }

    private <E extends WorkspaceEntity> void removeOrigins(@NotNull E entity,
                                                           @NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                           @NotNull EntityStorage storage,
                                                           @NotNull Project project) {
      Collection<? extends IndexableSetSelfDependentOrigin> origins = provider.getExistingEntityIteratorOrigins(entity, storage, project);
      if (!origins.isEmpty()) {
        for (IndexableSetSelfDependentOrigin origin : origins) {
          entitiesToOrigins.remove(entity, origin);
        }
      }
    }

    private void refreshEntities(@NotNull List<? extends WorkspaceEntity> entities, @NotNull Project project) {
      if (entities.isEmpty()) return;
      EntityStorage storage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
      for (IndexableEntityProvider<? extends WorkspaceEntity> provider : IndexableEntityProvider.EP_NAME.getExtensionList()) {
        if (provider instanceof IndexableEntityProvider.ExistingEx<?>) {
          handleEntitiesRefresh((IndexableEntityProvider.ExistingEx<?>)provider, entities, project, storage);
        }
      }
    }

    private <E extends WorkspaceEntity> void handleEntitiesRefresh(@NotNull IndexableEntityProvider.ExistingEx<E> provider,
                                                                   @NotNull List<? extends WorkspaceEntity> entities,
                                                                   @NotNull Project project,
                                                                   @NotNull EntityStorage storage) {
      Class<E> aClass = provider.getEntityClass();
      for (WorkspaceEntity entity : entities) {
        if (aClass.isInstance(entity)) {
          //noinspection unchecked
          Collection<? extends IndexableSetSelfDependentOrigin> origins =
            provider.getExistingEntityIteratorOrigins((E)entity, storage, project);
          //noinspection unchecked
          entitiesToOrigins.put(entity, (Collection<IndexableSetSelfDependentOrigin>)origins);
        }
      }
    }
  }

  private static class NonWorkspaceModelSnapshot {
    private final Collection<IndexableSetContributorSelfDependentOriginImpl> indexableSetOrigins = new ArrayList<>();
    private final Collection<SyntheticLibrarySelfDependentOriginImpl> syntheticLibrariesOrigins = new ArrayList<>();
    private final Set<VirtualFile> excludedFilesFromPolicies = new HashSet<>();
    private final Set<VirtualFilePointer> excludedModuleFilesFromPolicies = new HashSet<>();
    private final List<@NotNull Function<Sdk, List<VirtualFile>>> sdkExclusionFunctions = new ArrayList<>();

    @NotNull
    private static NonWorkspaceModelSnapshot buildSnapshot(@NotNull Project project) {
      NonWorkspaceModelSnapshot snapshot = new NonWorkspaceModelSnapshot();
      for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
        Collection<SyntheticLibrary> libraries = provider.getAdditionalProjectLibraries(project);
        for (SyntheticLibrary library : libraries) {
          SyntheticLibrarySelfDependentOriginImpl origin = new SyntheticLibrarySelfDependentOriginImpl(library,
                                                                                                       library.getAllRoots(),
                                                                                                       library.getExcludedRoots(),
                                                                                                       library.getUnitedExcludeCondition());
          snapshot.syntheticLibrariesOrigins.add(origin);
        }
      }

      for (IndexableSetContributor contributor : IndexableSetContributor.EP_NAME.getExtensionList()) {
        Set<VirtualFile> roots = contributor.getAdditionalRootsToIndex();
        IndexableSetContributorSelfDependentOriginImpl origin = new IndexableSetContributorSelfDependentOriginImpl(contributor, roots);
        snapshot.indexableSetOrigins.add(origin);

        Set<VirtualFile> projectRoots = contributor.getAdditionalProjectRootsToIndex(project);
        snapshot.indexableSetOrigins.add(new IndexableSetContributorSelfDependentOriginImpl(contributor, projectRoots));
      }

      List<ModuleRootModel> modules =
        ContainerUtil.map(ModuleManager.getInstance(project).getModules(), module -> ModuleRootManager.getInstance(module));
      for (DirectoryIndexExcludePolicy policy : DirectoryIndexExcludePolicy.getExtensions(project)) {
        VirtualFileManager fileManager = VirtualFileManager.getInstance();
        String[] urls = policy.getExcludeUrlsForProject();
        snapshot.excludedFilesFromPolicies.addAll(ContainerUtil.mapNotNull(urls, url -> fileManager.findFileByUrl(url)));

        Function<Sdk, List<VirtualFile>> strategy = policy.getExcludeSdkRootsStrategy();
        if (strategy != null) {
          snapshot.sdkExclusionFunctions.add(strategy);
        }

        for (ModuleRootModel model : modules) {
          ContainerUtil.addAll(snapshot.excludedModuleFilesFromPolicies, policy.getExcludeRootsForModule(model));
        }
      }
      return snapshot;
    }


    public boolean rebuildExcludedFromModuleRootsIfNeeded(@NotNull VersionedStorageChange storageChange,
                                                          @NotNull Project project) {
      boolean shouldRebuild = ContainerUtil.exists(storageChange.getChanges(ModuleEntity.class),
                                                   change -> change instanceof EntityChange.Added<ModuleEntity> ||
                                                             change instanceof EntityChange.Removed<?>);
      if (shouldRebuild) {
        excludedModuleFilesFromPolicies.clear();
        List<ModuleRootModel> modules =
          ContainerUtil.map(ModuleManager.getInstance(project).getModules(), module -> ModuleRootManager.getInstance(module));
        for (DirectoryIndexExcludePolicy policy : DirectoryIndexExcludePolicy.getExtensions(project)) {
          for (ModuleRootModel model : modules) {
            ContainerUtil.addAll(excludedModuleFilesFromPolicies, policy.getExcludeRootsForModule(model));
          }
        }
      }
      return shouldRebuild;
    }
  }

  public void workspaceModelChanged(@NotNull VersionedStorageChange storageChange) {
    boolean changed = workspaceModelStatus.changed(storageChange, project);
    if (nonWorkspaceSnapshot != null) {
      changed = changed || nonWorkspaceSnapshot.rebuildExcludedFromModuleRootsIfNeeded(storageChange, project);
    }
    if (changed) {
      regenerateResultingSnapshot();
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
    workspaceModelStatus.refreshEntities(entityWithChangedRoots, project);
    resetSnapshots();
  }
}