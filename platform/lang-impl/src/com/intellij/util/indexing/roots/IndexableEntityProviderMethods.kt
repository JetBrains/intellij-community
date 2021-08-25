// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

object IndexableEntityProviderMethods {
  private val LOG = Logger.getInstance(IndexableEntityProviderMethods::class.java)

  fun findModuleForEntity(entity: ModuleEntity, project: Project): Module? {
    val moduleName = entity.name
    val module = ModuleManager.getInstance(project).findModuleByName(moduleName)
    if (module == null) {
      LOG.warn("Failed to find module $moduleName")
    }
    return module
  }

  fun findLibraryForEntity(entity: LibraryEntity,
                           storageAfter: WorkspaceEntityStorage): Library? =
    storageAfter.libraryMap.getDataByEntity(entity)

  fun createIterators(entity: ModuleEntity, roots: List<VirtualFile>, project: Project): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    val module = findModuleForEntity(entity, project)
    if (module == null) {
      return emptyList()
    }
    return createIterators(module, roots)
  }

  fun createIterators(module: Module, roots: List<VirtualFile>): Set<IndexableFilesIterator> {
    return setOf(ModuleIndexableFilesIteratorImpl(module, roots, true))
  }

  fun createIterators(entity: ModuleEntity,
                      newRootsToIndex: List<VirtualFile>,
                      oldRootsToIndex: List<VirtualFile>,
                      project: Project): Collection<IndexableFilesIterator> {
    val roots: MutableList<VirtualFile> = ArrayList(newRootsToIndex)
    roots.removeAll(oldRootsToIndex)
    return createIterators(entity, roots, project)
  }

  fun createIterators(entity: ModuleEntity, root: VirtualFile?, project: Project): Collection<IndexableFilesIterator> {
    return root?.let { createIterators(entity, listOf(root), project) } ?: emptyList()
  }

  fun createIterators(entity: ModuleEntity, project: Project): Collection<IndexableFilesIterator> {
    val module = findModuleForEntity(entity, project)
    if (module == null) {
      return emptyList()
    }
    return ModuleIndexableFilesIteratorImpl.getModuleIterators(module)
  }

  fun createIterators(library: Library): Collection<IndexableFilesIterator> {
    return listOf(LibraryIndexableFilesIteratorImpl(library))
  }

  fun createIterators(sdk: Sdk): Collection<IndexableFilesIterator> {
    return listOf(SdkIndexableFilesIteratorImpl(sdk))
  }
}