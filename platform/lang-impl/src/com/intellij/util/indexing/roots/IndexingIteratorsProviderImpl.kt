// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.indexing.AdditionalIndexableFileSet
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.IndexingIteratorsProvider
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createIteratorForFileSet
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable

@ApiStatus.Internal
class IndexingIteratorsProviderImpl(
  private val project: Project,
) : IndexingIteratorsProvider {

  private val filesFromIndexableSetContributors = AdditionalIndexableFileSet(project)

  override fun getIndexingIterators(): List<IndexableFilesIterator> {
    return ReadAction.nonBlocking(Callable { doGetIndexingIterators() }).executeSynchronously()
  }

  override fun shouldBeIndexed(file: VirtualFile): Boolean {
    if (WorkspaceFileIndex.getInstance(project).isIndexable(file)) return true
    return filesFromIndexableSetContributors.isInSet(file)
  }

  override fun getModuleIndexingIterators(entity: ModuleEntity, entityStorage: EntityStorage): Collection<IndexableFilesIterator> {
    val module = entity.findModule(entityStorage)
    if (module == null) {
      return emptyList()
    }
    return IndexableEntityProviderMethods.createModuleContentIterators(module)
  }

  private fun doGetIndexingIterators(): List<IndexableFilesIterator> {
    val model = WorkspaceModel.getInstance(project)
    val index = WorkspaceFileIndexEx.getInstance(project)
    val storage = model.currentSnapshot
    val virtualFileUrlManager = model.getVirtualFileUrlManager()
    val moduleDependencyIndex by lazy { ModuleDependencyIndex.getInstance(project) }

    val iterators = ArrayList<IndexableFilesIterator>()
    val libraryOrigins = HashSet<LibraryOrigin>()

    index.visitFileSets { fileSet, entityPointer ->
      createIteratorForFileSet(fileSet, entityPointer, iterators, storage, virtualFileUrlManager, moduleDependencyIndex, index, libraryOrigins)
    }

    var addedFromDependenciesIndexedStatusService = false
    if (DependenciesIndexedStatusService.shouldBeUsed()) {
      val cacheService = DependenciesIndexedStatusService.getInstance(project)
      if (cacheService.shouldSaveStatus()) {
        addedFromDependenciesIndexedStatusService = true
        ProgressManager.checkCanceled()
        iterators.addAll(cacheService.saveLibsAndInstantiateLibraryIterators())
        ProgressManager.checkCanceled()
        iterators.addAll(cacheService.saveIndexableSetsAndInstantiateIterators())
        cacheService.saveExcludePolicies()
      }
    }

    if (!addedFromDependenciesIndexedStatusService) {
      for (contributor in IndexableSetContributor.EP_NAME.extensionList) {
        iterators.add(IndexableSetContributorFilesIterator(contributor, project))
        iterators.add(IndexableSetContributorFilesIterator(contributor))
      }
    }
    return iterators
  }
}