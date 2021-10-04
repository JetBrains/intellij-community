// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

object IndexableEntityProviderMethods {
  private val LOG = thisLogger()

  fun findModuleForEntity(entity: ModuleEntity, project: Project): Module? {
    val moduleName = entity.name
    val module = ModuleManager.getInstance(project).findModuleByName(moduleName)
    if (module == null) {
      LOG.warn("Failed to find module $moduleName")
    }
    return module
  }

  fun createIterators(entity: ModuleEntity, roots: List<VirtualFile>, project: Project): Collection<IndexableFilesIterator> {
    if (roots.isEmpty()) return emptyList()
    val module = findModuleForEntity(entity, project) ?: return emptyList()
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
    @Suppress("DEPRECATION")
    if (DefaultProjectIndexableFilesContributor.indexProjectBasedOnIndexableEntityProviders()) {
      val builders = mutableListOf<IndexableEntityProvider.IndexableIteratorBuilder>()
      val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
      for (provider in IndexableEntityProvider.EP_NAME.extensionList) {
        if (provider is IndexableEntityProvider.Existing) {
          builders.addAll(provider.getIteratorBuildersForExistingModule(entity, entityStorage, project))
        }
      }
      return IndexableIteratorBuilders.instantiateBuilders(builders, project, entityStorage)
    }
    else {
      val module = findModuleForEntity(entity, project)
      if (module == null) {
        return emptyList()
      }
      return ModuleIndexableFilesIteratorImpl.getModuleIterators(module)
    }
  }

  fun createIterators(library: LibraryBridge): Collection<IndexableFilesIterator> = createIterators(library, library.libraryId)

  fun createIterators(library: Library, libraryId: LibraryId): Collection<IndexableFilesIterator> {
    return listOf(LibraryBridgeIndexableFilesIteratorImpl(library, libraryId))
  }

  fun createIterators(sdk: Sdk): Collection<IndexableFilesIterator> {
    return listOf(SdkIndexableFilesIteratorImpl(sdk))
  }
}