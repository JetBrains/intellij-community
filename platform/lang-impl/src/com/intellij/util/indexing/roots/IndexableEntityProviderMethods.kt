// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.isModuleUnloaded
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

object IndexableEntityProviderMethods {
  fun createIterators(entity: ModuleEntity,
                      roots: List<VirtualFile>,
                      storage: EntityStorage): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    val module = entity.findModule(storage) ?: return emptyList()
    return createIterators(module, roots)
  }

  fun createIterators(module: Module, roots: List<VirtualFile>): Set<IndexableFilesIterator> {
    return setOf(ModuleIndexableFilesIteratorImpl(module, roots, true))
  }

  fun createIterators(entity: ModuleEntity, entityStorage: EntityStorage, project: Project): Collection<IndexableFilesIterator> {
    if (shouldIndexProjectBasedOnIndexableEntityProviders()) {
      if (entity.isModuleUnloaded(entityStorage)) return emptyList()
      val builders = mutableListOf<IndexableEntityProvider.IndexableIteratorBuilder>()
      for (provider in IndexableEntityProvider.EP_NAME.extensionList) {
        if (provider is IndexableEntityProvider.Existing) {
          builders.addAll(provider.getIteratorBuildersForExistingModule(entity, entityStorage, project))
        }
      }
      return IndexableIteratorBuilders.instantiateBuilders(builders, project, entityStorage)
    }
    else {
      val module = entity.findModule(entityStorage)
      if (module == null) {
        return emptyList()
      }
      return ModuleIndexableFilesIteratorImpl.getModuleIterators(module)
    }
  }

  fun createIterators(sdk: Sdk): Collection<IndexableFilesIterator> {
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
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    return@runReadAction storage.entities(LibraryEntity::class.java).firstOrNull { it.name == name }?.let {
      storage.libraryMap.getDataByEntity(it)
    }?.run {
      LibraryIndexableFilesIteratorImpl.createIteratorList(this)
    } ?: emptyList()
  }

  fun getExcludedFiles(entity: ContentRootEntity): List<VirtualFile> {
    return ContainerUtil.mapNotNull(entity.excludedUrls) { param -> param.url.virtualFile }
  }
}