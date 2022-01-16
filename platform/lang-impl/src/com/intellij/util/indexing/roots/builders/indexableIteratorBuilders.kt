// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

object IndexableIteratorBuilders {
  val logger = thisLogger()

  fun forModuleRoots(moduleId: ModuleId, urls: Collection<VirtualFileUrl>): Collection<IndexableIteratorBuilder> =
    if (urls.isEmpty()) emptyList() else listOf(ModuleRootsIteratorBuilder(moduleId, urls))

  fun forModuleRoots(moduleId: ModuleId, url: VirtualFileUrl): Collection<IndexableIteratorBuilder> =
    listOf(ModuleRootsIteratorBuilder(moduleId, url))

  fun forModuleRoots(moduleId: ModuleId,
                     newRoots: List<VirtualFileUrl>,
                     oldRoots: List<VirtualFileUrl>): Collection<IndexableIteratorBuilder> {
    val roots = newRoots.toMutableList()
    roots.removeAll(oldRoots)
    return forModuleRoots(moduleId, roots)
  }

  fun forLibraryEntity(libraryId: LibraryId, dependencyChecked: Boolean): Collection<IndexableIteratorBuilder> =
    listOf(LibraryIdIteratorBuilder(libraryId, dependencyChecked))

  fun forSdk(sdkName: String, sdkType: String): Collection<IndexableIteratorBuilder> =
    listOf(SdkIteratorBuilder(sdkName, sdkType))

  fun forInheritedSdk(): Collection<IndexableIteratorBuilder> = listOf(InheritedSdkIteratorBuilder)

  fun forModuleContent(moduleId: ModuleId): Collection<IndexableIteratorBuilder> = listOf(FullModuleContentIteratorBuilder(moduleId))

  fun instantiateBuilders(builders: List<IndexableIteratorBuilder>,
                          project: Project,
                          entityStorage: WorkspaceEntityStorage): List<IndexableFilesIterator> {
    if (builders.isEmpty()) return emptyList()
    val result = ArrayList<IndexableFilesIterator>(builders.size)
    var buildersToProceed = builders
    val handlers = IndexableIteratorBuilderHandler.EP_NAME.extensionList
    for (handler in handlers) {
      ProgressManager.checkCanceled()
      val partition = buildersToProceed.partition { handler.accepts(it) }
      buildersToProceed = partition.second
      if (partition.first.isNotEmpty()) {
        result.addAll(handler.instantiate(partition.first, project, entityStorage))
      }
    }
    if (buildersToProceed.isNotEmpty()) {
      logger.error("Failed to find handlers for IndexableIteratorBuilders: ${buildersToProceed};\n" +
                   "available handlers: $handlers")
    }
    return result
  }
}

internal data class LibraryIdIteratorBuilder(val libraryId: LibraryId,
                                             val dependencyChecked: Boolean) : IndexableIteratorBuilder

internal data class SdkIteratorBuilder(val sdkName: String, val sdkType: String) : IndexableIteratorBuilder

internal object InheritedSdkIteratorBuilder : IndexableIteratorBuilder

internal data class FullModuleContentIteratorBuilder(val moduleId: ModuleId) : IndexableIteratorBuilder

internal class ModuleRootsIteratorBuilder(val moduleId: ModuleId, val urls: Collection<VirtualFileUrl>) : IndexableIteratorBuilder {
  constructor(moduleId: ModuleId, url: VirtualFileUrl) : this(moduleId, listOf(url))
}
