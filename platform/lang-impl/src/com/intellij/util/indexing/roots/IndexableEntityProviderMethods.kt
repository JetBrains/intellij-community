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
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityReference
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.indexing.IndexableFilesIndex
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.origin.IndexingRootHolder
import com.intellij.util.indexing.roots.origin.IndexingSourceRootHolder
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule

object IndexableEntityProviderMethods {
  fun createIterators(entity: ModuleEntity,
                      roots: IndexingRootHolder,
                      storage: EntityStorage): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    val module = entity.findModule(storage) ?: return emptyList()
    return createIterators(module, roots)
  }

  fun createIterators(module: Module, roots: IndexingRootHolder): Collection<IndexableFilesIterator> {
    return ModuleIndexableFilesIteratorImpl.createIterators(module, roots)
  }

  fun createModuleContentIterators(module: Module): Collection<IndexableFilesIterator> {
    return ModuleIndexableFilesIteratorImpl.createIterators(module)
  }

  fun createIterators(entity: ModuleEntity, entityStorage: EntityStorage, project: Project): Collection<IndexableFilesIterator> {
    return IndexableFilesIndex.getInstance(project).getModuleIndexingIterators(entity, entityStorage)
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

  fun createExternalEntityIterators(reference: EntityReference<*>,
                                    roots: IndexingSourceRootHolder,
                                    presentation: IndexableIteratorPresentation?): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    return listOf(ExternalEntityIndexableIteratorImpl(reference, roots, presentation))
  }

  fun createCustomKindEntityIterators(reference: EntityReference<*>,
                                      roots: IndexingRootHolder,
                                      presentation: IndexableIteratorPresentation?): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    return listOf(CustomKindEntityIteratorImpl(reference, roots, presentation))
  }

  fun createGenericContentEntityIterators(reference: EntityReference<*>,
                                          rootHolder: IndexingRootHolder,
                                          presentation: IndexableIteratorPresentation?): Collection<IndexableFilesIterator> {
    if (rootHolder.isEmpty()) return emptyList()
    return listOf(GenericContentEntityIteratorImpl(reference, rootHolder, presentation))
  }

  fun createModuleAwareContentEntityIterators(module: Module,
                                              reference: EntityReference<*>,
                                              roots: IndexingRootHolder,
                                              presentation: IndexableIteratorPresentation?): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    return listOf(ModuleAwareContentEntityIteratorImpl(module, reference, roots, presentation))
  }

  fun createForIndexableSetContributor(contributor: IndexableSetContributor,
                                       isProjectAware: Boolean,
                                       roots: Collection<VirtualFile>): Collection<IndexableFilesIterator> =
    listOf(IndexableSetContributorFilesIterator(contributor, roots, isProjectAware))

  fun createForSyntheticLibrary(library: SyntheticLibrary,
                                roots: Collection<VirtualFile>): Collection<IndexableFilesIterator> =
    listOf(SyntheticLibraryIndexableFilesIteratorImpl(SyntheticLibraryIndexableFilesIteratorImpl.getName(library), library, roots))
}