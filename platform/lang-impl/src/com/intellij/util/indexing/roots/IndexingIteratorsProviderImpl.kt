// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.util.SmartList
import com.intellij.util.indexing.AdditionalIndexableFileSet
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.IndexingIteratorsProvider
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService
import com.intellij.util.indexing.roots.origin.IndexingRootHolder
import com.intellij.util.indexing.roots.origin.MutableIndexingUrlSourceRootHolder
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
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
    if (WorkspaceFileIndex.getInstance(project).isInWorkspace(file)) return true
    return filesFromIndexableSetContributors.isInSet(file)
  }


  private fun doGetIndexingIterators(): List<IndexableFilesIterator> {
    val model = WorkspaceModel.getInstance(project)
    val index = WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexImpl
    val virtualFileUrlManager = model.getVirtualFileUrlManager()

    val iterators = ArrayList<IndexableFilesIterator>()

    index.visitFileSets { fileSet: WorkspaceFileSet, entityPointer, recursive ->
      val files = fileSet as WorkspaceFileSetWithCustomData<*>
      val root = fileSet.root
      val customData = fileSet.data
      if (customData is ModuleRelatedRootData) {
        iterators.add(ModuleFilesIteratorImpl(customData.module, root, recursive, true))
      }
      else if (files.kind.isContent) {
        val rootHolder: IndexingRootHolder
        if (recursive) {
          rootHolder = IndexingRootHolder.fromFile(root)
        }
        else {
          rootHolder = IndexingRootHolder.fromFileNonRecursive(root)
        }
        iterators.add(GenericContentEntityIteratorImpl(entityPointer, rootHolder, null))
      }
      else {
        val storage = model.currentSnapshot
        val entity = entityPointer.resolve(storage)
        if (entity is LibraryEntity) {
          val sourceLibraryRoot = SmartList<VirtualFile>()
          val libraryRoot = SmartList<VirtualFile>()

          if (fileSet.kind == WorkspaceFileKind.EXTERNAL_SOURCE) {
            sourceLibraryRoot.add(root)
          }
          else {
            libraryRoot.add(root)
          }
          val libraryBridge = storage.libraryMap.getDataByEntity(entity)
          if (libraryBridge != null) {
            val iterator =
              LibraryIndexableFilesIteratorImpl.createIterator(libraryBridge, libraryRoot, sourceLibraryRoot)
            if (iterator != null) {
              iterators.add(iterator)
            }
          }
        }
        else if (entity is SdkEntity) {
          val sdkBridge = storage.sdkMap.getDataByEntity(entity)
          if (sdkBridge != null) {
            iterators.add(SdkIndexableFilesIteratorImpl.createIterator(sdkBridge, listOf(root)))
          }
        }
        else if (fileSet.kind == WorkspaceFileKind.CUSTOM) {
          val rootHolder: IndexingRootHolder
          if (recursive) {
            rootHolder = IndexingRootHolder.fromFile(root)
          }
          else {
            rootHolder = IndexingRootHolder.fromFileNonRecursive(root)
          }
          iterators.add(CustomKindEntityIteratorImpl(entityPointer, rootHolder, null))
        }
        else {
          val virtualFileUrl = root.toVirtualFileUrl(virtualFileUrlManager)
          val holder = MutableIndexingUrlSourceRootHolder()
          if (fileSet.kind == WorkspaceFileKind.EXTERNAL_SOURCE) {
            if (recursive) {
              holder.sourceRoots.add(virtualFileUrl)
            }
            else {
              holder.nonRecursiveSourceRoots.add(virtualFileUrl)
            }
          }
          else {
            if (recursive) {
              holder.roots.add(virtualFileUrl)
            }
            else {
              holder.nonRecursiveRoots.add(virtualFileUrl)
            }
          }
          iterators.addAll(
            IndexableEntityProviderMethods.createExternalEntityIterators(entityPointer, holder, null)
          )
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
}