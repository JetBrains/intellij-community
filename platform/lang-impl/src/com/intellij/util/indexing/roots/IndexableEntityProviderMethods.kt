// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.indexing.IndexingIteratorsProvider
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder
import com.intellij.util.indexing.roots.origin.IndexingUrlSourceRootHolder
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IndexableEntityProviderMethods {
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
}