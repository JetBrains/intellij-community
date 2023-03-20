// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.AdditionalIndexableFileSet;
import com.intellij.util.indexing.IndexableFilesIndex;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData;
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleContentOrSourceRootData;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSetVisitor;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.indexing.roots.IndexableEntityProviderMethods.INSTANCE;
import static com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl.Companion;

@ApiStatus.Experimental
@ApiStatus.Internal
public class IndexableFilesIndexImpl implements IndexableFilesIndex {
  @NotNull
  private final Project project;
  private final AdditionalIndexableFileSet filesFromIndexableSetContributors;

  @NotNull
  public static IndexableFilesIndexImpl getInstanceImpl(@NotNull Project project) {
    return (IndexableFilesIndexImpl)IndexableFilesIndex.getInstance(project);
  }


  public IndexableFilesIndexImpl(@NotNull Project project) {
    this.project = project;
    filesFromIndexableSetContributors = new AdditionalIndexableFileSet(project);
  }

  @Override
  public boolean shouldBeIndexed(@NotNull VirtualFile file) {
    if (WorkspaceFileIndex.getInstance(project).isInWorkspace(file)) return true;
    return filesFromIndexableSetContributors.isInSet(file);
  }

  @Override
  public @NotNull List<IndexableFilesIterator> getIndexingIterators() {
    return ReadAction.nonBlocking(this::doGetIndexingIterators).expireWith(project).executeSynchronously();
  }

  @NotNull
  private List<IndexableFilesIterator> doGetIndexingIterators() {
    EntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
    List<IndexableFilesIterator> iterators = new ArrayList<>();

    List<Sdk> sdks = new ArrayList<>();
    ModuleDependencyIndex moduleDependencyIndex = ModuleDependencyIndex.getInstance(project);
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (moduleDependencyIndex.hasDependencyOn(sdk)) {
        sdks.add(sdk);
      }
    }
    if (sdks.isEmpty()) {
      Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (projectSdk != null) {
        sdks.add(projectSdk);
      }
    }
    for (Sdk sdk : sdks) {
      ProgressManager.checkCanceled();
      iterators.addAll(INSTANCE.createIterators(sdk));
    }

    Set<IndexableSetOrigin> libraryOrigins = new HashSet<>();
    LibraryTablesRegistrar tablesRegistrar = LibraryTablesRegistrar.getInstance();
    Sequence<LibraryTable> libs = SequencesKt.asSequence(tablesRegistrar.getCustomLibraryTables().iterator());
    libs = SequencesKt.plus(libs, tablesRegistrar.getLibraryTable());
    for (LibraryTable libraryTable : SequencesKt.asIterable(libs)) {
      for (Library library : libraryTable.getLibraries()) {
        ProgressManager.checkCanceled();
        if (moduleDependencyIndex.hasDependencyOn(library)) {
          for (IndexableFilesIterator iterator : Companion.createIteratorList(library)) {
            if (libraryOrigins.add(iterator.getOrigin())) {
              iterators.add(iterator);
            }
          }
        }
      }
    }

    WorkspaceIndexingRootsBuilder builder =
      WorkspaceIndexingRootsBuilder.Companion.registerEntitiesFromContributors(project, entityStorage, null);
    builder.addIteratorsFromRoots(iterators, libraryOrigins, entityStorage);

    boolean addedFromDependenciesIndexedStatusService = false;
    if (DependenciesIndexedStatusService.shouldBeUsed()) {
      DependenciesIndexedStatusService cacheService = DependenciesIndexedStatusService.getInstance(project);
      if (cacheService.shouldSaveStatus()) {
        addedFromDependenciesIndexedStatusService = true;
        ProgressManager.checkCanceled();
        iterators.addAll(cacheService.saveIndexableSetsAndInstantiateIterators());
        ProgressManager.checkCanceled();
        iterators.addAll(cacheService.saveLibsAndInstantiateLibraryIterators());
        cacheService.saveExcludePolicies();
      }
    }

    if (!addedFromDependenciesIndexedStatusService) {
      for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
        for (SyntheticLibrary library : provider.getAdditionalProjectLibraries(project)) {
          iterators.add(new SyntheticLibraryIndexableFilesIteratorImpl(library));
        }
      }

      for (IndexableSetContributor contributor : IndexableSetContributor.EP_NAME.getExtensionList()) {
        iterators.add(new IndexableSetContributorFilesIterator(contributor, project));
        iterators.add(new IndexableSetContributorFilesIterator(contributor));
      }
    }
    return iterators;
  }

  @Override
  public @NotNull Collection<IndexableFilesIterator> getModuleIndexingIterators(@NotNull ModuleEntity entity,
                                                                                @NotNull EntityStorage entityStorage) {
    ModuleBridge module = ModuleEntityUtils.findModule(entity, entityStorage);
    if (module == null) {
      return Collections.emptyList();
    }
    List<VirtualFile> roots = getModuleRootsToIndex(module);
    if (roots.isEmpty()) return Collections.emptyList();
    return Collections.singletonList(new ModuleIndexableFilesIteratorImpl(module, roots, true));
  }

  @NotNull
  private List<VirtualFile> getModuleRootsToIndex(@NotNull Module module) {
    List<VirtualFile> roots = ReadAction.nonBlocking(() -> {
      if (project.isDisposed()) return null;
      WorkspaceFileIndexEx index = (WorkspaceFileIndexEx)WorkspaceFileIndex.getInstance(project);
      List<VirtualFile> files = new SmartList<>();
      index.visitFileSets(new WorkspaceFileSetVisitor() {
        @Override
        public void visitIncludedRoot(@NotNull WorkspaceFileSet fileSet) {
          if (!(fileSet instanceof WorkspaceFileSetWithCustomData<?>)) return;
          ModuleContentOrSourceRootData data =
            ObjectUtils.tryCast(((WorkspaceFileSetWithCustomData<?>)fileSet).getData(), ModuleContentOrSourceRootData.class);
          if (data != null && data.getModule().equals(module)) {
            files.add(fileSet.getRoot());
          }
        }
      });
      return files;
    }).executeSynchronously();
    return IndexingRootsCollectionUtil.optimizeRoots(roots);
  }
}