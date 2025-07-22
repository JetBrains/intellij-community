// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.SmartList
import com.intellij.util.indexing.IndexingIteratorsProvider
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.origin.IndexingRootHolder
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder
import com.intellij.util.indexing.roots.origin.IndexingUrlSourceRootHolder
import com.intellij.util.indexing.roots.origin.MutableIndexingUrlSourceRootHolder
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IndexableEntityProviderMethods {

  /**
   * Creates an iterator for [fileSet] and adds it to [iterators]
   */
  fun createIteratorForFileSet(
    fileSet: WorkspaceFileSet,
    entityPointer: EntityPointer<*>,
    iterators: MutableList<IndexableFilesIterator>,
    storage: EntityStorage,
    virtualFileUrlManager: VirtualFileUrlManager,
    moduleDependencyIndex: ModuleDependencyIndex,
    index: WorkspaceFileIndexEx,
    libraryOrigins: HashSet<LibraryOrigin>
  ) {
    fileSet as WorkspaceFileSetWithCustomData<*>
    if (!fileSet.kind.isIndexable) return

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
        if (moduleDependencyIndex.hasDependencyOn(entity.symbolicId)) {
          val libraryBridge = storage.libraryMap.getDataByEntity(entity)
          if (libraryBridge != null) {
            val sourceLibraryRoot = SmartList<VirtualFile>()
            val libraryRoot = SmartList<VirtualFile>()
            if (fileSet.kind == WorkspaceFileKind.EXTERNAL_SOURCE) {
              sourceLibraryRoot.add(root)
            }
            else {
              libraryRoot.add(root)
            }
            val iterator =
              LibraryIndexableFilesIteratorImpl.createIterator(libraryBridge, libraryRoot, sourceLibraryRoot)
            if (iterator != null && libraryOrigins.add(iterator.origin)) {
              iterators.add(iterator)
            }
          }
        }
      }
      else if (entity is SdkEntity) {
        if (moduleDependencyIndex.hasDependencyOn(entity.symbolicId)) {
          val sdkType = SdkType.findByName(entity.type)
          iterators.add(SdkIndexableFilesIteratorImpl.createIterator(
            entity.name,
            sdkType,
            entity.homePath?.url,
            listOf(root)))
        }
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
        val virtualFileUrl = root.toVirtualFileUrl(virtualFileUrlManager)
        val holder = MutableIndexingUrlSourceRootHolder()
        if (fileSet.kind == WorkspaceFileKind.EXTERNAL_SOURCE) {
          if (fileSet.recursive) {
            holder.sourceRoots.add(virtualFileUrl)
          }
          else {
            holder.nonRecursiveSourceRoots.add(virtualFileUrl)
          }
        }
        else {
          if (fileSet.recursive) {
            holder.roots.add(virtualFileUrl)
          }
          else {
            holder.nonRecursiveRoots.add(virtualFileUrl)
          }
        }
        iterators.addAll(createExternalEntityIterators(entityPointer, holder))
      }
    }
  }

  fun createIterators(entity: ModuleEntity,
                      roots: IndexingUrlRootHolder,
                      storage: EntityStorage): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    val module = entity.findModule(storage) ?: return emptyList()
    return createIterators(module, roots)
  }

  fun createIterators(module: Module, roots: IndexingUrlRootHolder): Collection<IndexableFilesIterator> {
    return ModuleIndexableFilesIteratorImpl.createIterators(module, roots)
  }

  fun createModuleContentIterators(module: Module): Collection<IndexableFilesIterator> {
    return ModuleIndexableFilesIteratorImpl.createIterators(module)
  }

  fun createIterators(entity: ModuleEntity, entityStorage: EntityStorage, project: Project): Collection<IndexableFilesIterator> {
    return IndexingIteratorsProvider.getInstance(project).getModuleIndexingIterators(entity, entityStorage)
  }

  fun createIterators(sdk: Sdk): List<IndexableFilesIterator> {
    return listOf(SdkIndexableFilesIteratorImpl.createIterator(sdk))
  }

  private fun getLibIteratorsByName(libraryTable: LibraryTable, name: String): List<IndexableFilesIterator>? =
    libraryTable.getLibraryByName(name)?.run { LibraryIndexableFilesIteratorImpl.createIteratorList(this) }

  fun createLibraryIterators(name: String, project: Project): List<IndexableFilesIterator> = runReadAction {
    val registrar = LibraryTablesRegistrar.getInstance()
    getLibIteratorsByName(registrar.libraryTable, name)?.also { return@runReadAction it }
    for (customLibraryTable in registrar.customLibraryTables) {
      getLibIteratorsByName(customLibraryTable, name)?.also { return@runReadAction it }
    }
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    return@runReadAction storage.entities(LibraryEntity::class.java).firstOrNull { it.name == name }?.let {
      storage.libraryMap.getDataByEntity(it)
    }?.run {
      LibraryIndexableFilesIteratorImpl.createIteratorList(this)
    } ?: emptyList()
  }

  fun getExcludedFiles(entity: ContentRootEntity): List<VirtualFile> {
    return entity.excludedUrls.mapNotNull { param -> param.url.virtualFile }
  }

  fun createExternalEntityIterators(pointer: EntityPointer<*>,
                                    urlRoots: IndexingUrlSourceRootHolder): Collection<IndexableFilesIterator> {
    val roots = urlRoots.toSourceRootHolder()
    if (roots.isEmpty()) return emptyList()
    return listOf(ExternalEntityIndexableIteratorImpl(pointer, roots))
  }

  fun createCustomKindEntityIterators(pointer: EntityPointer<*>,
                                      urlRootHolder: IndexingUrlRootHolder): Collection<IndexableFilesIterator> {
    val rootHolder = urlRootHolder.toRootHolder()
    if (rootHolder.isEmpty()) return emptyList()
    return listOf(CustomKindEntityIteratorImpl(pointer, rootHolder))
  }

  fun createGenericContentEntityIterators(pointer: EntityPointer<*>,
                                          urlRootHolder: IndexingUrlRootHolder): Collection<IndexableFilesIterator> {
    val rootHolder = urlRootHolder.toRootHolder()
    if (rootHolder.isEmpty()) return emptyList()
    return listOf(GenericContentEntityIteratorImpl(pointer, rootHolder))
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