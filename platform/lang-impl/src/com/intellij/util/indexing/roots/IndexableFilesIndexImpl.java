// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.Function;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.EntityIndexingServiceEx;
import com.intellij.util.indexing.IndexableFilesIndex;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.roots.kind.IndexableSetIterableOrigin;
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin;
import com.intellij.util.indexing.roots.kind.SdkOrigin;
import com.intellij.util.indexing.roots.origin.IndexableSetContributorIterableOriginImpl;
import com.intellij.util.indexing.roots.origin.ModuleRootIterableOriginImpl;
import com.intellij.util.indexing.roots.origin.SdkIterableOriginImpl;
import com.intellij.util.indexing.roots.origin.SyntheticLibraryIterableOriginImpl;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyListener;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.EntityReference;
import com.intellij.workspaceModel.storage.VersionedStorageChange;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Experimental
@ApiStatus.Internal
public class IndexableFilesIndexImpl implements IndexableFilesIndex {
  private static final Logger LOG = Logger.getInstance(IndexableFilesIndexImpl.class);
  @NotNull
  private static final FileTypeRegistry ourFileTypes = FileTypeRegistry.getInstance();
  @NotNull
  private final Project project;
  @NotNull
  private final SnapshotHandler snapshotHandler;

  @NotNull
  public static IndexableFilesIndexImpl getInstanceImpl(@NotNull Project project) {
    return (IndexableFilesIndexImpl)IndexableFilesIndex.getInstance(project);
  }


  public IndexableFilesIndexImpl(@NotNull Project project) {
    this.project = project;
    snapshotHandler = new SnapshotHandler(project);
    ModuleDependencyIndex.Companion.getInstance(project).addListener(snapshotHandler.createModuleDependencyListener());
    ProjectRootManagerEx.getInstanceEx(project).addProjectJdkListener(() -> {
      snapshotHandler.updateWorkspaceSnapshot(snapshot -> {
        Sdk newProjectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        return snapshot.projectJdkChanged(newProjectSdk);
      });
    });
  }

  @Override
  public boolean shouldBeIndexed(@NotNull VirtualFile file) {
    return getOrigin(file) != null;
  }

  @Nullable
  private IndexableSetIterableOrigin getOrigin(@NotNull VirtualFile file) {
    VirtualFile currentFile = file;
    ImmutableSetMultimap<VirtualFile, IndexableSetIterableOrigin> roots = snapshotHandler.getResultingSnapshot(project).roots;
    boolean isExcludedFromContent = false;
    while (currentFile != null) {
      Collection<IndexableSetIterableOrigin> origins = roots.get(currentFile);
      for (IndexableSetIterableOrigin origin : origins) {
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
    private final ImmutableSetMultimap<VirtualFile, IndexableSetIterableOrigin> roots;
    private final ImmutableSet<IndexableSetIterableOrigin> origins;

    ResultingSnapshot(@NotNull WorkspaceModelSnapshot status,
                      @NotNull NonWorkspaceModelSnapshot snapshot) {
      Collection<IndexableSetIterableOrigin> origins = new ArrayList<>();
      Set<VirtualFile> excludedModuleFilesFromPolicies = new HashSet<>(snapshot.excludedFilesFromPolicies);
      excludedModuleFilesFromPolicies.addAll(
        ContainerUtil.mapNotNull(snapshot.excludedModuleFilesFromPolicies, pointer -> pointer.getFile()));
      for (IndexableSetIterableOrigin origin : status.getOrigins()) {
        origin = patchOriginIfNeeded(origin, snapshot, excludedModuleFilesFromPolicies);
        origins.add(origin);
      }
      origins.addAll(snapshot.indexableSetOrigins);
      origins.addAll(snapshot.syntheticLibrariesOrigins);

      ImmutableSetMultimap.Builder<VirtualFile, IndexableSetIterableOrigin> rootsBuilder = new ImmutableSetMultimap.Builder<>();
      for (IndexableSetIterableOrigin origin : origins) {
        for (VirtualFile root : origin.getIterationRoots()) {
          rootsBuilder.put(root, origin);
        }
      }
      roots = rootsBuilder.build();
      this.origins = ImmutableSet.copyOf(origins);
    }

    @NotNull
    private static IndexableSetIterableOrigin patchOriginIfNeeded(@NotNull IndexableSetIterableOrigin origin,
                                                                  @NotNull NonWorkspaceModelSnapshot snapshot,
                                                                  @NotNull Set<VirtualFile> excludedModuleFilesFromPolicies) {
      if (origin instanceof SdkIterableOriginImpl) {
        List<VirtualFile> excludedRootsForSdk = new ArrayList<>();
        for (Function<Sdk, List<VirtualFile>> exclusionFunction : snapshot.sdkExclusionFunctions) {
          List<VirtualFile> roots = exclusionFunction.fun(((SdkOrigin)origin).getSdk());
          if (roots != null && !roots.isEmpty()) {
            excludedRootsForSdk.addAll(roots);
          }
        }
        if (!excludedRootsForSdk.isEmpty()) {
          return ((SdkIterableOriginImpl)origin).copyWithAdditionalExcludedFiles(excludedRootsForSdk);
        }
      }
      else if (origin instanceof ModuleRootIterableOriginImpl && !excludedModuleFilesFromPolicies.isEmpty()) {
        return ((ModuleRootIterableOriginImpl)origin).copyWithAdditionalExcludedFiles(excludedModuleFilesFromPolicies);
      }
      return origin;
    }
  }

  private static class NonWorkspaceModelSnapshot {
    private final Collection<IndexableSetContributorIterableOriginImpl> indexableSetOrigins;
    private final Collection<SyntheticLibraryIterableOriginImpl> syntheticLibrariesOrigins;
    private final Set<VirtualFile> excludedFilesFromPolicies;
    private final Set<VirtualFilePointer> excludedModuleFilesFromPolicies;
    private final List<@NotNull Function<Sdk, List<VirtualFile>>> sdkExclusionFunctions;

    private NonWorkspaceModelSnapshot(Collection<IndexableSetContributorIterableOriginImpl> indexableSetOrigins,
                                      Collection<SyntheticLibraryIterableOriginImpl> syntheticLibrariesOrigins,
                                      Set<VirtualFile> excludedFilesFromPolicies,
                                      Set<VirtualFilePointer> excludedModuleFilesFromPolicies,
                                      List<@NotNull Function<Sdk, List<VirtualFile>>> sdkExclusionFunctions) {
      this.indexableSetOrigins = List.copyOf(indexableSetOrigins);
      this.syntheticLibrariesOrigins = List.copyOf(syntheticLibrariesOrigins);
      this.excludedFilesFromPolicies = Set.copyOf(excludedFilesFromPolicies);
      this.excludedModuleFilesFromPolicies = Set.copyOf(excludedModuleFilesFromPolicies);
      this.sdkExclusionFunctions = List.copyOf(sdkExclusionFunctions);
    }

    @RequiresReadLock
    @NotNull
    private static NonWorkspaceModelSnapshot buildSnapshot(@NotNull Project project) {
      Collection<SyntheticLibraryIterableOriginImpl> syntheticLibrariesOrigins = new ArrayList<>();
      for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
        Collection<SyntheticLibrary> libraries = provider.getAdditionalProjectLibraries(project);
        for (SyntheticLibrary library : libraries) {
          String name = library instanceof ItemPresentation ? ((ItemPresentation)library).getPresentableText() : null;
          SyntheticLibraryIterableOriginImpl origin = new SyntheticLibraryIterableOriginImpl(library,
                                                                                             library.getAllRoots(),
                                                                                             library.getExcludedRoots(),
                                                                                             library.getUnitedExcludeCondition(),
                                                                                             name);
          syntheticLibrariesOrigins.add(origin);
        }
      }
      Collection<IndexableSetContributorIterableOriginImpl> indexableSetOrigins = new ArrayList<>();
      for (IndexableSetContributor contributor : IndexableSetContributor.EP_NAME.getExtensionList()) {
        Set<VirtualFile> roots = contributor.getAdditionalRootsToIndex();
        String presentableText = (contributor instanceof ItemPresentation) ? ((ItemPresentation)contributor).getPresentableText() : null;
        String debugName = contributor.getDebugName();
        indexableSetOrigins.add(new IndexableSetContributorIterableOriginImpl(presentableText, debugName, false,
                                                                              contributor, roots));

        Set<VirtualFile> projectRoots = contributor.getAdditionalProjectRootsToIndex(project);
        indexableSetOrigins.add(new IndexableSetContributorIterableOriginImpl(presentableText, debugName, true,
                                                                              contributor, projectRoots));
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
    List<EntityReference<WorkspaceEntity>> referencesToEntitiesWithChangedRoots = EntityIndexingServiceEx.getInstanceEx().getReferencesToEntitiesWithChangedRoots(indexingInfos);
    snapshotHandler.updateSnapshots(snapshots -> {
      WorkspaceModelSnapshot workspaceSnapshot =
        snapshots.myWorkspaceModelSnapshot.createWithRefreshedEntitiesIfNeeded(referencesToEntitiesWithChangedRoots, project);
      if (workspaceSnapshot != null) {
        snapshots = snapshots.copyWithWorkspaceStatus(workspaceSnapshot);
      }

      snapshots = snapshots.copyWithNonWorkspaceSnapshot(null);
      return snapshots;
    });
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
          NonWorkspaceModelSnapshot snapshot =
            ReadAction.nonBlocking(() -> NonWorkspaceModelSnapshot.buildSnapshot(project)).executeSynchronously();
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
      updateSnapshots(snapshots -> snapshots.copyWithNonWorkspaceSnapshot(null));
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
    }
  }

  public void referencedSdkAdded(@NotNull Sdk sdk) {
    snapshotHandler.updateWorkspaceSnapshot(snapshot -> snapshot.referencedSdkAdded(sdk));
  }

  public void referencedSdkChanged(@NotNull Sdk sdk) {
    snapshotHandler.updateWorkspaceSnapshot(snapshot -> snapshot.referencedSdkChanged(sdk));
  }

  public void referencedSdkRemoved(@NotNull Sdk sdk) {
    snapshotHandler.updateWorkspaceSnapshot(snapshot -> snapshot.referencedSdkRemoved(sdk));
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