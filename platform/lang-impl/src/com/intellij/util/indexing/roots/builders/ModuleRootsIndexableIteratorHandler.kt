// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.ide.isEqualOrParentOf
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

class ModuleRootsIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    builder is ModuleRootsIteratorBuilder || builder is FullModuleContentIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: WorkspaceEntityStorage): List<IndexableFilesIterator> {
    val fullIndexedModules: Set<ModuleId> = builders.mapNotNull { (it as? FullModuleContentIteratorBuilder)?.moduleId }.toSet()

    @Suppress("UNCHECKED_CAST")
    val partialIterators = builders.filter {
      it is ModuleRootsIteratorBuilder && !fullIndexedModules.contains(it.moduleId)
    } as List<ModuleRootsIteratorBuilder>
    val partialIteratorsMap = partialIterators.groupBy { builder -> builder.moduleId }

    val result = mutableListOf<IndexableFilesIterator>()
    fullIndexedModules.forEach { moduleId ->
      entityStorage.resolve(moduleId)?.also { entity ->
        result.addAll(IndexableEntityProviderMethods.createIterators(entity, entityStorage, project))
      }
    }

    val moduleMap = entityStorage.moduleMap
    partialIteratorsMap.forEach { pair ->
      entityStorage.resolve(pair.key)?.also { entity ->
        result.addAll(IndexableEntityProviderMethods.createIterators(entity, resolveRoots(pair.value), moduleMap, project))
      }
    }
    return result
  }

  private fun resolveRoots(builders: List<ModuleRootsIteratorBuilder>): List<VirtualFile> {
    if (PlatformUtils.isRider() || PlatformUtils.isCLion()) {
      return builders.flatMap { builder -> builder.urls }.mapNotNull { url -> url.virtualFile }
    }
    val roots = mutableListOf<VirtualFileUrl>()
    for (builder in builders) {
      for (root in builder.urls) {
        var isChild = false
        val it = roots.iterator()
        while (it.hasNext()) {
          val next = it.next()
          if (next.isEqualOrParentOf(root)) {
            isChild = true
            break
          }
          if (root.isEqualOrParentOf(next)) {
            it.remove()
          }
        }
        if (!isChild) {
          roots.add(root)
        }
      }
    }
    return roots.mapNotNull { url -> url.virtualFile }
  }
}