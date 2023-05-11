// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.ide.isEqualOrParentOf
import com.intellij.workspaceModel.ide.virtualFile
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

class ModuleRootsIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    builder is ModuleRootsIteratorBuilder || builder is ModuleRootsFileBasedIteratorBuilder ||
    builder is ModuleAwareCustomizedContentEntityBuilder<*> || builder is FullModuleContentIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    val fullIndexedModules: Set<ModuleId> = builders.mapNotNull { (it as? FullModuleContentIteratorBuilder)?.moduleId }.toSet()

    val partialIteratorsMap = builders.filterIsInstance(ModuleRootsIteratorBuilder::class.java).filter {
      !fullIndexedModules.contains(it.moduleId)
    }.groupBy { builder -> builder.moduleId }

    val custom = builders.filterIsInstance(
      ModuleAwareCustomizedContentEntityBuilder::class.java).filter {
      !fullIndexedModules.contains(it.moduleId)
    }

    val usual = builders.filterIsInstance(ModuleRootsFileBasedIteratorBuilder::class.java)
    val partialFileBasedIteratorsMap = usual.filter {
      !fullIndexedModules.contains(it.moduleId)
    }.groupBy { builder -> builder.moduleId }.toMutableMap()

    val result = mutableListOf<IndexableFilesIterator>()
    fullIndexedModules.forEach { moduleId ->
      entityStorage.resolve(moduleId)?.also { entity ->
        result.addAll(IndexableEntityProviderMethods.createIterators(entity, entityStorage, project))
      }
    }

    partialIteratorsMap.forEach { pair ->
      entityStorage.resolve(pair.key)?.also { entity ->
        val files = partialFileBasedIteratorsMap.remove(pair.key) ?: emptyList()
        result.addAll(IndexableEntityProviderMethods.createIterators(entity, resolveRoots(pair.value, files), entityStorage))
      }
    }

    partialFileBasedIteratorsMap.forEach { pair ->
      entityStorage.resolve(pair.key)?.also { entity ->
        result.addAll(IndexableEntityProviderMethods.createIterators(entity, resolveRoots(emptyList(), pair.value), entityStorage))
      }
    }

    fun <E : WorkspaceEntity> registerIterators(builder: ModuleAwareCustomizedContentEntityBuilder<E>, entityStorage: EntityStorage) {
      entityStorage.resolve(builder.moduleId)?.findModule(entityStorage)?.also { module ->
        result.addAll(builder.customization.createModuleAwareContentIterators(module, builder.entityReference, builder.roots))
      }
    }

    custom.forEach { builder -> registerIterators(builder, entityStorage) }

    return result
  }

  private fun resolveRoots(builders: List<ModuleRootsIteratorBuilder>,
                           fileBasedBuilders: List<ModuleRootsFileBasedIteratorBuilder>): List<VirtualFile> {
    if (PlatformUtils.isRider() || PlatformUtils.isCLion()) {
      return builders.flatMap { builder -> builder.urls }.mapNotNull { url -> url.virtualFile } +
             fileBasedBuilders.flatMap { builder -> builder.files }
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
    //todo[lene] optimize where possible
    return roots.mapNotNull { url -> url.virtualFile } + fileBasedBuilders.flatMap { builder -> builder.files }
  }
}