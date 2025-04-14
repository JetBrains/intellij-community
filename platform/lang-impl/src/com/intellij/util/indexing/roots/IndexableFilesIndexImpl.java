// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl.Companion;

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
    if (WorkspaceFileIndex.getInstance(project).isInWorkspace(file)) return true;
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

    ModuleDependencyIndex moduleDependencyIndex = ModuleDependencyIndex.getInstance(project);
    if (!Registry.is("ide.workspace.model.sdk.remove.custom.processing")) {
      List<Sdk> sdks = new ArrayList<>();
      for (Sdk sdk : ProjectJdkTable.getInstance(project).getAllJdks()) {
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
        iterators.addAll(IndexableEntityProviderMethods.INSTANCE.createIterators(sdk));
      }

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
    }

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