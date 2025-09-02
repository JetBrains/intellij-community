// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder
import com.intellij.util.indexing.roots.origin.MutableIndexingUrlRootHolder
import com.intellij.util.indexing.roots.selectRootVirtualFileUrls

class ModuleRootsIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    builder is ModuleRootsIteratorBuilder || builder is ModuleRootsFileBasedIteratorBuilder ||
    builder is FullModuleContentIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    val fullIndexedModules: Set<ModuleId> = builders.mapNotNull { (it as? FullModuleContentIteratorBuilder)?.moduleId }.toSet()

    val partialIteratorsMap = builders.filterIsInstance<ModuleRootsIteratorBuilder>().filter {
      !fullIndexedModules.contains(it.moduleId)
    }.groupBy { builder -> builder.moduleId }


    val usual = builders.filterIsInstance<ModuleRootsFileBasedIteratorBuilder>()
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

    return result
  }

  private fun resolveRoots(builders: List<ModuleRootsIteratorBuilder>,
                           fileBasedBuilders: List<ModuleRootsFileBasedIteratorBuilder>): IndexingUrlRootHolder {
    val rootsFromRecursiveBuilders = selectRootVirtualFileUrls(builders.flatMap { it.urls })
    val holder = fileBasedBuilders.foldRight(MutableIndexingUrlRootHolder()) { builder, holder -> holder.addRoots(builder.files); holder }
    holder.roots.addAll(rootsFromRecursiveBuilders)
    return holder
  }
}