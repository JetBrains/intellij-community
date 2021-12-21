// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.ExternalEntityMapping
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

object IndexableEntityProviderMethods {
  private val LOG = thisLogger()

  fun findModuleForEntity(entity: ModuleEntity, storage: WorkspaceEntityStorage, project: Project): Module? =
    findModuleForEntity(entity, storage.moduleMap, project)

  fun findModuleForEntity(entity: ModuleEntity, map: ExternalEntityMapping<ModuleBridge>, project: Project): Module? {
    val moduleName = entity.name
    val module = ModuleManager.getInstance(project).findModuleByName(moduleName)
    if (module == null && !isModuleUnloaded(entity, map)) {
      LOG.error("Failed to find module $moduleName")
    }
    return module
  }

  fun isModuleUnloaded(entity: ModuleEntity, mapping: ExternalEntityMapping<ModuleBridge>): Boolean =
    mapping.getDataByEntity(entity) == null

  fun createIterators(entity: ModuleEntity,
                      roots: List<VirtualFile>,
                      mapping: ExternalEntityMapping<ModuleBridge>,
                      project: Project): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    val module = findModuleForEntity(entity, mapping, project) ?: return emptyList()
    return createIterators(module, roots)
  }

  fun createIterators(module: Module, roots: List<VirtualFile>): Set<IndexableFilesIterator> {
    return setOf(ModuleIndexableFilesIteratorImpl(module, roots, true))
  }

  fun createIterators(entity: ModuleEntity, entityStorage: WorkspaceEntityStorage, project: Project): Collection<IndexableFilesIterator> {
    @Suppress("DEPRECATION")
    if (DefaultProjectIndexableFilesContributor.indexProjectBasedOnIndexableEntityProviders()) {
      if (isModuleUnloaded(entity, entityStorage.moduleMap)) return emptyList()
      val builders = mutableListOf<IndexableEntityProvider.IndexableIteratorBuilder>()
      for (provider in IndexableEntityProvider.EP_NAME.extensionList) {
        if (provider is IndexableEntityProvider.Existing) {
          builders.addAll(provider.getIteratorBuildersForExistingModule(entity, entityStorage, project))
        }
      }
      return IndexableIteratorBuilders.instantiateBuilders(builders, project, entityStorage)
    }
    else {
      val module = findModuleForEntity(entity, entityStorage, project)
      if (module == null) {
        return emptyList()
      }
      return ModuleIndexableFilesIteratorImpl.getModuleIterators(module)
    }
  }

  fun createIterators(library: Library): Collection<IndexableFilesIterator> = createIteratorList(library)

  fun createIterators(sdk: Sdk): Collection<IndexableFilesIterator> {
    return listOf(SdkIndexableFilesIteratorImpl(sdk))
  }

  private fun createIteratorList(library: Library): List<IndexableFilesIterator> = listOf(LibraryIndexableFilesIteratorImpl(library))

  private fun getLibIteratorsByName(libraryTable: LibraryTable, name: String): List<IndexableFilesIterator>? =
    libraryTable.getLibraryByName(name)?.run { createIteratorList(this) }

  fun createLibraryIterators(name: String, project: Project): List<IndexableFilesIterator> {
    val registrar = LibraryTablesRegistrar.getInstance()
    getLibIteratorsByName(registrar.libraryTable, name)?.also { return it }
    for (customLibraryTable in registrar.customLibraryTables) {
      getLibIteratorsByName(customLibraryTable, name)?.also { return it }
    }
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    storage.entities(LibraryEntity::class.java).firstOrNull { it.name == name }?.let { storage.libraryMap.getDataByEntity(it) }?.also {
      return createIteratorList(it)
    }
    return emptyList()
  }
}