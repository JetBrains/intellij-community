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

class ModuleRootsIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    builder is ModuleRootsFileBasedIteratorBuilder ||
    builder is FullModuleContentIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    val fullIndexedModules: Set<ModuleId> = builders.mapNotNull { (it as? FullModuleContentIteratorBuilder)?.moduleId }.toSet()

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

    partialFileBasedIteratorsMap.forEach { pair ->
      entityStorage.resolve(pair.key)?.also { entity ->
        result.addAll(IndexableEntityProviderMethods.createIterators(entity, resolveRoots(pair.value), entityStorage))
      }
    }

    return result
  }

  private fun resolveRoots(fileBasedBuilders: List<ModuleRootsFileBasedIteratorBuilder>): IndexingUrlRootHolder {
    val holder = fileBasedBuilders.foldRight(MutableIndexingUrlRootHolder()) { builder, holder -> holder.addRoots(builder.files); holder }
    return holder
  }
}