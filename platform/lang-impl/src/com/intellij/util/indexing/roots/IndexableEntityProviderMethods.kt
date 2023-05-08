// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableFilesIndex
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.ide.virtualFile
import com.intellij.workspaceModel.storage.EntityReference
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

  fun createIterators(module: Module, roots: List<VirtualFile>): Collection<IndexableFilesIterator> {
    return setOf(ModuleIndexableFilesIteratorImpl(module, roots, true))
  }

  fun createIterators(entity: ModuleEntity, entityStorage: EntityStorage, project: Project): Collection<IndexableFilesIterator> {
    if (shouldIndexProjectBasedOnIndexableEntityProviders()) {
      if (IndexableFilesIndex.isEnabled()) {
        return IndexableFilesIndex.getInstance(project).getModuleIndexingIterators(entity, entityStorage)
      }
      val builders = mutableListOf<IndexableEntityProvider.IndexableIteratorBuilder>()
      for (provider in IndexableEntityProvider.EP_NAME.extensionList) {
        if (provider is IndexableEntityProvider.Existing) {
          builders.addAll(provider.getIteratorBuildersForExistingModule(entity, entityStorage, project))
        }
      }
      // so far there are no WorkspaceFileIndexContributors giving module roots, so requesting them is time-consuming and useless
      return IndexableIteratorBuilders.instantiateBuilders(builders, project, entityStorage)
    }
    else {
      val module = entity.findModule(entityStorage)
      if (module == null) {
        return emptyList()
      }
      @Suppress("DEPRECATION")
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

  fun createExternalEntityIterators(reference: EntityReference<*>,
                                    roots: Collection<VirtualFile>,
                                    sourceRoots: Collection<VirtualFile>): Collection<IndexableFilesIterator> {
    if (roots.isEmpty() && sourceRoots.isEmpty()) return emptyList()
    return listOf(ExternalEntityIndexableIteratorImpl(reference, roots, sourceRoots))
  }

  fun createGenericContentEntityIterators(reference: EntityReference<*>,
                                          roots: Collection<VirtualFile>): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    return listOf(GenericContentEntityIteratorImpl(reference, roots))
  }

  fun createModuleAwareContentEntityIterators(module: Module,
                                              reference: EntityReference<*>,
                                              roots: Collection<VirtualFile>): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    return listOf(ModuleAwareContentEntityIteratorImpl(module, reference, roots))
  }

  fun createForIndexableSetContributor(contributor: IndexableSetContributor,
                                       isProjectAware: Boolean,
                                       roots: Collection<VirtualFile>): Collection<IndexableFilesIterator> =
    listOf(IndexableSetContributorFilesIterator(contributor, roots, isProjectAware))

  fun createForSyntheticLibrary(library: SyntheticLibrary,
                                roots: Collection<VirtualFile>): Collection<IndexableFilesIterator> =
    listOf(SyntheticLibraryIndexableFilesIteratorImpl(SyntheticLibraryIndexableFilesIteratorImpl.getName(library), library, roots))
}