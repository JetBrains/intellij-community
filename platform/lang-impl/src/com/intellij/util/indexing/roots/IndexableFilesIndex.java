// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

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
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.Function;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.EntityIndexingServiceEx;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.roots.kind.IndexableSetSelfDependentOrigin;
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin;
import com.intellij.util.indexing.roots.kind.SdkOrigin;
import com.intellij.util.indexing.roots.origin.IndexableSetContributorSelfDependentOriginImpl;
import com.intellij.util.indexing.roots.origin.ModuleRootSelfDependentOriginImpl;
import com.intellij.util.indexing.roots.origin.SdkSelfDependentOriginImpl;
import com.intellij.util.indexing.roots.origin.SyntheticLibrarySelfDependentOriginImpl;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyListener;
import com.intellij.workspaceModel.storage.*;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity;
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
  private final SnapshotHandler snapshotHandler;

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
    snapshotHandler = new SnapshotHandler(project);
    ModuleDependencyIndex.Companion.getInstance(project).addListener(snapshotHandler.createModuleDependencyListener());
  }

  @RequiresBackgroundThread
  public boolean shouldBeIndexed(@NotNull VirtualFile file) {
    return getOrigin(file) != null;
  }

  @Nullable
  public IndexableSetSelfDependentOrigin getOrigin(@NotNull VirtualFile file) {
    VirtualFile currentFile = file;
    ImmutableSetMultimap<VirtualFile, IndexableSetSelfDependentOrigin> roots = snapshotHandler.getResultingSnapshot(project).roots;
    boolean isExcludedFromContent = false;
    while (currentFile != null) {
      Collection<IndexableSetSelfDependentOrigin> origins = roots.get(currentFile);
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

  private static class ResultingSnapshot {
    private final ImmutableSetMultimap<VirtualFile, IndexableSetSelfDependentOrigin> roots;

    ResultingSnapshot(@NotNull WorkspaceModelSnapshot status,
                      @NotNull NonWorkspaceModelSnapshot snapshot) {
      Collection<IndexableSetSelfDependentOrigin> origins = new ArrayList<>();
      Set<VirtualFile> excludedModuleFilesFromPolicies = new HashSet<>(snapshot.excludedFilesFromPolicies);
      excludedModuleFilesFromPolicies.addAll(
        ContainerUtil.mapNotNull(snapshot.excludedModuleFilesFromPolicies, pointer -> pointer.getFile()));
      for (IndexableSetSelfDependentOrigin origin : status.getOrigins()) {
        origin = patchOriginIfNeeded(origin, snapshot, excludedModuleFilesFromPolicies);
        origins.add(origin);
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

    @NotNull
    private static IndexableSetSelfDependentOrigin patchOriginIfNeeded(@NotNull IndexableSetSelfDependentOrigin origin,
                                                                       @NotNull NonWorkspaceModelSnapshot snapshot,
                                                                       @NotNull Set<VirtualFile> excludedModuleFilesFromPolicies) {
      if (origin instanceof SdkSelfDependentOriginImpl) {
        List<VirtualFile> excludedRootsForSdk = new ArrayList<>();
        for (Function<Sdk, List<VirtualFile>> exclusionFunction : snapshot.sdkExclusionFunctions) {
          List<VirtualFile> roots = exclusionFunction.fun(((SdkOrigin)origin).getSdk());
          if (roots != null && !roots.isEmpty()) {
            excludedRootsForSdk.addAll(roots);
          }
        }
        if (!excludedRootsForSdk.isEmpty()) {
          return ((SdkSelfDependentOriginImpl)origin).copyWithAdditionalExcludedFiles(excludedRootsForSdk);
        }
      }
      else if (origin instanceof ModuleRootSelfDependentOriginImpl && !excludedModuleFilesFromPolicies.isEmpty()) {
        return ((ModuleRootSelfDependentOriginImpl)origin).copyWithAdditionalExcludedFiles(excludedModuleFilesFromPolicies);
      }
      return origin;
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
    snapshotHandler.updateWorkspaceSnapshot(snapshot -> snapshot.createChangedIfNeeded(storageChange, project));
    snapshotHandler.updateNonWorkspaceSnapshot(snapshot -> {
      return snapshot == null ? null : snapshot.rebuildExcludedFromModuleRootsIfNeeded(storageChange, project);
    });
  }

  public void beforeRootsChanged() {
    snapshotHandler.resetSnapshots();
  }

  public void afterRootsChanged(boolean fileTypes,
                                @NotNull List<? extends RootsChangeRescanningInfo> indexingInfos,
                                boolean isFromWorkspaceOnly) {
    if (fileTypes) {
      LOG.assertTrue(indexingInfos.isEmpty(), "File type root change event shouldn't have indexingInfos");
      LOG.assertTrue(!isFromWorkspaceOnly, "File type root change event has nothing to do with Workspace Model");
      snapshotHandler.resetSnapshots();
      return;
    }
    if (isFromWorkspaceOnly) return;
    List<WorkspaceEntity> entityWithChangedRoots = EntityIndexingServiceEx.getInstanceEx().getEntitiesWithChangedRoots(indexingInfos);
    snapshotHandler.updateWorkspaceSnapshot(snapshot -> snapshot.createWithRefreshedEntitiesIfNeeded(entityWithChangedRoots, project));
  }

  private static class SnapshotHandler {
    private final Object LOCK = new Object();
    @NotNull
    private Snapshots mySnapshots;

    private SnapshotHandler(@NotNull Project project) {
      Snapshots snapshots = Snapshots.create(project);
      synchronized (LOCK) {
        mySnapshots = snapshots;
      }
    }

    @NotNull
    public Snapshots getSnapshots() {
      synchronized (LOCK) {
        return mySnapshots;
      }
    }

    @NotNull
    private ResultingSnapshot getResultingSnapshot(@NotNull Project project) {
      ResultingSnapshot resultingSnapshot;
      NonWorkspaceModelSnapshot nonWorkspaceSnapshot;
      int nonWorkspaceCounter;
      WorkspaceModelSnapshot workspaceStatus;
      int workspaceCounter;
      while (true) {
        synchronized (LOCK) {
          workspaceStatus = mySnapshots.myWorkspaceModelSnapshot;
          workspaceCounter = mySnapshots.myWorkspaceModificationCounter;
          nonWorkspaceSnapshot = mySnapshots.myNonWorkspaceModelSnapshot;
          nonWorkspaceCounter = mySnapshots.myNonWorkspaceModificationCounter;
          resultingSnapshot = mySnapshots.myResultingSnapshot;
        }
        if (resultingSnapshot != null) return resultingSnapshot;

        if (nonWorkspaceSnapshot == null) {
          NonWorkspaceModelSnapshot snapshot = NonWorkspaceModelSnapshot.buildSnapshot(project);
          synchronized (LOCK) {
            if (mySnapshots.myNonWorkspaceModificationCounter == nonWorkspaceCounter) {
              mySnapshots = mySnapshots.copyWithNonWorkspaceSnapshot(snapshot);
            }
          }
        }
        else {
          ResultingSnapshot snapshot = new ResultingSnapshot(workspaceStatus, nonWorkspaceSnapshot);
          synchronized (LOCK) {
            if (mySnapshots.myWorkspaceModificationCounter == workspaceCounter &&
                mySnapshots.myNonWorkspaceModificationCounter == nonWorkspaceCounter) {
              mySnapshots = mySnapshots.copyWithResultingSnapshot(snapshot);
              return snapshot;
            }
          }
        }
      }
    }

    public void updateWorkspaceSnapshot(Function<@NotNull WorkspaceModelSnapshot, @Nullable WorkspaceModelSnapshot> updater) {
      WorkspaceModelSnapshot status;
      int counter;
      while (true) {
        synchronized (LOCK) {
          status = mySnapshots.myWorkspaceModelSnapshot;
          counter = mySnapshots.myWorkspaceModificationCounter;
        }
        WorkspaceModelSnapshot update = updater.fun(status);
        if (update == null) return;
        synchronized (LOCK) {
          if (mySnapshots.myWorkspaceModificationCounter == counter) {
            mySnapshots = mySnapshots.copyWithWorkspaceStatus(update);
            return;
          }
        }
      }
    }

    public void updateNonWorkspaceSnapshot(Function<@Nullable NonWorkspaceModelSnapshot, @Nullable NonWorkspaceModelSnapshot> updater) {
      NonWorkspaceModelSnapshot snapshot;
      int snapshotModificationCounter;
      while (true) {
        synchronized (LOCK) {
          snapshot = mySnapshots.myNonWorkspaceModelSnapshot;
          snapshotModificationCounter = mySnapshots.myNonWorkspaceModificationCounter;
        }
        NonWorkspaceModelSnapshot update = updater.fun(snapshot);
        if (update == null) {
          return;
        }
        synchronized (LOCK) {
          if (snapshotModificationCounter == mySnapshots.myNonWorkspaceModificationCounter) {
            mySnapshots = mySnapshots.copyWithNonWorkspaceSnapshot(update);
            return;
          }
        }
      }
    }

    public void resetSnapshots() {
      updateSnapshots(snapshots -> snapshots.createReset());
    }

    private void updateSnapshots(@NotNull Function<Snapshots, Snapshots> updater) {
      Snapshots snapshots;
      while (true) {
        synchronized (LOCK) {
          snapshots = mySnapshots;
        }
        Snapshots update = updater.fun(snapshots);
        if (update == null) {
          return;
        }
        synchronized (LOCK) {
          if (mySnapshots == snapshots) {
            mySnapshots = update;
            return;
          }
        }
      }
    }

    @NotNull
    public ModuleDependencyListener createModuleDependencyListener() {
      return new MyModuleDependencyListener();
    }

    private class MyModuleDependencyListener implements ModuleDependencyListener {
      @Override
      public void referencedLibraryAdded(@NotNull Library library) {
        updateWorkspaceSnapshot(snapshot -> snapshot.referencedLibraryAdded(library));
      }

      @Override
      public void referencedLibraryChanged(@NotNull Library library) {
        updateWorkspaceSnapshot(snapshot -> snapshot.referencedLibraryChanged(library));
      }

      @Override
      public void referencedLibraryRemoved(@NotNull Library library) {
        updateWorkspaceSnapshot(snapshot -> snapshot.referencedLibraryRemoved(library));
      }

      @Override
      public void addedDependencyOn(@NotNull Sdk sdk) {
        updateWorkspaceSnapshot(snapshot -> snapshot.addedDependencyOn(sdk));
      }

      @Override
      public void removedDependencyOn(@NotNull Sdk sdk) {
        updateWorkspaceSnapshot(snapshot -> snapshot.removedDependencyOn(sdk));
      }

      @Override
      public void referencedSdkAdded(@NotNull Sdk sdk) {
        updateWorkspaceSnapshot(snapshot -> snapshot.referencedSdkAdded(sdk));
      }

      @Override
      public void referencedSdkChanged(@NotNull Sdk sdk) {
        updateWorkspaceSnapshot(snapshot -> snapshot.referencedSdkChanged(sdk));
      }

      @Override
      public void referencedSdkRemoved(@NotNull Sdk sdk) {
        updateWorkspaceSnapshot(snapshot -> snapshot.referencedSdkRemoved(sdk));
      }
    }
  }

  private static class Snapshots {
    @NotNull
    private final WorkspaceModelSnapshot myWorkspaceModelSnapshot;
    private final int myWorkspaceModificationCounter;
    @Nullable
    private final NonWorkspaceModelSnapshot myNonWorkspaceModelSnapshot;
    private final int myNonWorkspaceModificationCounter;
    @Nullable
    private final ResultingSnapshot myResultingSnapshot;

    private Snapshots(@NotNull WorkspaceModelSnapshot workspaceModelSnapshot,
                      int workspaceModificationCounter,
                      @Nullable NonWorkspaceModelSnapshot nonWorkspaceModelSnapshot,
                      int nonWorkspaceModificationCounter,
                      @Nullable ResultingSnapshot resultingSnapshot) {
      myWorkspaceModelSnapshot = workspaceModelSnapshot;
      myWorkspaceModificationCounter = workspaceModificationCounter;
      myNonWorkspaceModelSnapshot = nonWorkspaceModelSnapshot;
      myNonWorkspaceModificationCounter = nonWorkspaceModificationCounter;
      myResultingSnapshot = resultingSnapshot;
    }

    private static Snapshots create(@NotNull Project project) {
      return new Snapshots(WorkspaceModelSnapshot.create(project), 0, null, 0, null);
    }

    public Snapshots createReset() {
      return new Snapshots(myWorkspaceModelSnapshot, myWorkspaceModificationCounter, null,
                           myNonWorkspaceModelSnapshot == null ? myNonWorkspaceModificationCounter : myNonWorkspaceModificationCounter + 1,
                           null);
    }

    @NotNull
    public Snapshots copyWithWorkspaceStatus(@NotNull WorkspaceModelSnapshot update) {
      return new Snapshots(update, myWorkspaceModificationCounter + 1,
                           myNonWorkspaceModelSnapshot, myNonWorkspaceModificationCounter,
                           null);
    }

    @NotNull
    public Snapshots copyWithNonWorkspaceSnapshot(@Nullable NonWorkspaceModelSnapshot update) {
      return new Snapshots(myWorkspaceModelSnapshot, myWorkspaceModificationCounter,
                           update, myNonWorkspaceModificationCounter + 1,
                           null);
    }

    @NotNull
    public Snapshots copyWithResultingSnapshot(@NotNull ResultingSnapshot snapshot) {
      return new Snapshots(myWorkspaceModelSnapshot, myWorkspaceModificationCounter,
                           myNonWorkspaceModelSnapshot, myNonWorkspaceModificationCounter,
                           snapshot);
    }
  }
}