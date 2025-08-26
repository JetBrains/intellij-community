// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @deprecated Use {@link IndexingIteratorsProviderImpl IndexingIteratorsProviderImpl}
 */
@ApiStatus.Experimental
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public final class IndexableFilesIndexImpl implements IndexableFilesIndex {
  private final @NotNull Project project;
  private final AdditionalIndexableFileSet filesFromIndexableSetContributors;

  public static @NotNull IndexableFilesIndexImpl getInstanceImpl(@NotNull Project project) {
    return (IndexableFilesIndexImpl)IndexableFilesIndex.getInstance(project);
  }


  public IndexableFilesIndexImpl(@NotNull Project project) {
    this.project = project;
    filesFromIndexableSetContributors = new AdditionalIndexableFileSet(project);
  }

  @Override
  public boolean shouldBeIndexed(@NotNull VirtualFile file) {
    if (WorkspaceFileIndex.getInstance(project).isIndexable(file)) return true;
    return filesFromIndexableSetContributors.isInSet(file);
  }

  @Override
  public @NotNull List<IndexableFilesIterator> getIndexingIterators() {
    return ReadAction.nonBlocking(this::doGetIndexingIterators).expireWith(project).executeSynchronously();
  }

  private @NotNull List<IndexableFilesIterator> doGetIndexingIterators() {
    EntityStorage entityStorage = WorkspaceModel.getInstance(project).getCurrentSnapshot();
    List<IndexableFilesIterator> iterators = new ArrayList<>();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      iterators.addAll(IndexableEntityProviderMethods.INSTANCE.createModuleContentIterators(module));
    }

    Set<IndexableSetOrigin> libraryOrigins = new HashSet<>();

    WorkspaceIndexingRootsBuilder.Companion.Settings settings = new WorkspaceIndexingRootsBuilder.Companion.Settings();
    settings.setCollectExplicitRootsForModules(false);
    settings.setRetainCondition(contributor -> contributor.getStorageKind() == EntityStorageKind.MAIN);
    WorkspaceIndexingRootsBuilder builder =
      WorkspaceIndexingRootsBuilder.Companion.registerEntitiesFromContributors(entityStorage, settings);
    List<IndexableFilesIterator> iteratorsFromRoots = builder.getIteratorsFromRoots(entityStorage, project, libraryOrigins);
    iterators.addAll(iteratorsFromRoots);

    boolean addedFromDependenciesIndexedStatusService = false;
    if (DependenciesIndexedStatusService.shouldBeUsed()) {
      DependenciesIndexedStatusService cacheService = DependenciesIndexedStatusService.getInstance(project);
      if (cacheService.shouldSaveStatus()) {
        addedFromDependenciesIndexedStatusService = true;
        ProgressManager.checkCanceled();
        iterators.addAll(cacheService.saveLibsAndInstantiateLibraryIterators());
        ProgressManager.checkCanceled();
        iterators.addAll(cacheService.saveIndexableSetsAndInstantiateIterators());
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
}