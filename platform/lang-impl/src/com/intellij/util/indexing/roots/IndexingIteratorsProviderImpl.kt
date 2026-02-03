// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.indexing.AdditionalIndexableFileSet
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.IndexingIteratorsProvider
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.origin.IndexingRootHolder
import com.intellij.util.indexing.roots.origin.IndexingSourceRootHolder
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
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

    val iterators = ArrayList<IndexableFilesIterator>()
    val libraryOrigins = HashSet<LibraryOrigin>()

    index.visitFileSets { fileSet, entityPointer ->
      fileSet as WorkspaceFileSetWithCustomData<*>
      if (!fileSet.kind.isIndexable) return@visitFileSets

      val root = fileSet.root
      val customData = fileSet.data
      if (customData is ModuleRelatedRootData) {
        if (!isNestedRootOfModuleContent(root, customData.module, index)) {
          iterators.add(ModuleFilesIteratorImpl(customData.module, root, fileSet.recursive, true))
        }
      }
      else if (fileSet.kind.isContent) {
        val rootHolder: IndexingRootHolder
        if (fileSet.recursive) {
          rootHolder = IndexingRootHolder.fromFile(root)
        }
        else {
          rootHolder = IndexingRootHolder.fromFileNonRecursive(root)
        }
        iterators.add(GenericContentEntityIteratorImpl(entityPointer, rootHolder))
      }
      else {
        val entity = entityPointer.resolve(storage)
        if (entity is LibraryEntity) {
          val (origin, iterator) = processLibraryEntity(entity, fileSet)
          if (libraryOrigins.add(origin)) {
            iterators.add(iterator)
          }
        }
        else if (entity is SdkEntity) {
          iterators.add(GenericDependencyIterator.forSdkEntity(
            sdkName = entity.name,
            sdkType = SdkType.findByName(entity.type),
            sdkHome = entity.homePath?.url,
            root = fileSet.root
          ))
        }
        else if (fileSet.kind == WorkspaceFileKind.CUSTOM) {
          val rootHolder: IndexingRootHolder
          if (fileSet.recursive) {
            rootHolder = IndexingRootHolder.fromFile(root)
          }
          else {
            rootHolder = IndexingRootHolder.fromFileNonRecursive(root)
          }
          iterators.add(CustomKindEntityIteratorImpl(entityPointer, rootHolder))
        }
        else {
          val rootHolder: IndexingSourceRootHolder
          if (fileSet.kind == WorkspaceFileKind.EXTERNAL_SOURCE) {
            rootHolder = IndexingSourceRootHolder.fromFiles(emptyList(), listOf(root))
          }
          else {
            rootHolder = IndexingSourceRootHolder.fromFiles(listOf(root), emptyList())
          }
          iterators.add(IndexableEntityProviderMethods.createExternalEntityIterators(entityPointer, rootHolder))
        }
      }
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

  private fun isNestedRootOfModuleContent(root: VirtualFile, module: Module, workspaceFileIndex: WorkspaceFileIndexEx): Boolean {
    val parent = root.getParent()
    if (parent == null) {
      return false
    }
    val fileInfo = workspaceFileIndex.getFileInfo(
      parent,
      honorExclusion = false,
      includeContentSets = true,
      includeContentNonIndexableSets = true,
      includeExternalSets = false,
      includeExternalSourceSets = false,
      includeCustomKindSets = false
    )
    return fileInfo.findFileSet { fileSet -> hasRecursiveRootFromModuleContent(fileSet, module) } != null
  }

  private fun hasRecursiveRootFromModuleContent(fileSet: WorkspaceFileSetWithCustomData<*>, module: Module): Boolean {
    if (!fileSet.recursive) {
      return false
    }
    return isInContent(fileSet, module)
  }

  private fun isInContent(fileSet: WorkspaceFileSetWithCustomData<*>, module: Module): Boolean {
    val data = fileSet.data
    return data is ModuleRelatedRootData && module == data.module
  }
}