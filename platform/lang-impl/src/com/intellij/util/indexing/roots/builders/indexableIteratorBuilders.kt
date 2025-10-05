// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder
import com.intellij.util.indexing.roots.origin.IndexingUrlSourceRootHolder

internal object IndexableIteratorBuilders {
  private val logger = thisLogger()

  fun forModuleRootsFileBased(moduleId: ModuleId, rootHolder: IndexingUrlRootHolder): Collection<IndexableIteratorBuilder> =
    if (rootHolder.isEmpty()) emptyList() else listOf(ModuleRootsFileBasedIteratorBuilder(moduleId, rootHolder))

  @JvmOverloads
  fun forLibraryEntity(libraryId: LibraryId,
                       dependencyChecked: Boolean,
                       roots: Collection<VirtualFile>? = null,
                       sourceRoots: Collection<VirtualFile>? = null): Collection<IndexableIteratorBuilder> =
    listOf(LibraryIdIteratorBuilder(libraryId, roots, sourceRoots, null, dependencyChecked))

  fun forLibraryEntity(libraryId: LibraryId,
                       dependencyChecked: Boolean,
                       roots: IndexingUrlSourceRootHolder): Collection<IndexableIteratorBuilder> =
    listOf(LibraryIdIteratorBuilder(libraryId, null, null, roots, dependencyChecked))


  fun forSdkEntity(sdkId: SdkId,
                   roots: IndexingUrlRootHolder): Collection<IndexableIteratorBuilder> = listOf(SdkIteratorBuilder(sdkId.name, sdkId.type, null, roots))

  @JvmOverloads
  fun forSdk(sdkName: String, sdkType: String, file: Collection<VirtualFile>? = null): IndexableIteratorBuilder = SdkIteratorBuilder(sdkName, sdkType, file)

  fun forSdk(sdkId: SdkId, file: Collection<VirtualFile>? = null): IndexableIteratorBuilder = forSdk(sdkId.name, sdkId.type, file)

  fun forInheritedSdk(): Collection<IndexableIteratorBuilder> = listOf(InheritedSdkIteratorBuilder)

  fun forModuleContent(moduleId: ModuleId): Collection<IndexableIteratorBuilder> = listOf(FullModuleContentIteratorBuilder(moduleId))

  fun <E : WorkspaceEntity> forGenericContentEntity(entityPointer: EntityPointer<E>,
                                                    roots: IndexingUrlRootHolder): Collection<IndexableIteratorBuilder> =
    if (roots.isEmpty()) emptyList()
    else listOf(GenericContentEntityBuilder(entityPointer, roots))

  fun <E : WorkspaceEntity> forExternalEntity(entityPointer: EntityPointer<E>,
                                              urlRoots: IndexingUrlSourceRootHolder): Collection<IndexableIteratorBuilder> =
    if (urlRoots.isEmpty()) emptyList()
    else listOf(ExternalEntityIteratorBuilder(entityPointer, urlRoots))

  fun <E : WorkspaceEntity> forCustomKindEntity(entityPointer: EntityPointer<E>,
                                                roots: IndexingUrlRootHolder): Collection<IndexableIteratorBuilder> =
    if (roots.isEmpty()) emptyList()
    else listOf(CustomKindEntityBuilder(entityPointer, roots))

  fun instantiateBuilders(builders: Collection<IndexableIteratorBuilder>,
                          project: Project,
                          entityStorage: EntityStorage): List<IndexableFilesIterator> {
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
                                             val roots: Collection<VirtualFile>? = null,
                                             val sourceRoots: Collection<VirtualFile>? = null,
                                             val rootUrls: IndexingUrlSourceRootHolder? = null,
                                             val dependencyChecked: Boolean = false) : IndexableIteratorBuilder

internal data class SdkIteratorBuilder(val sdkName: String,
                                       val sdkType: String,
                                       val roots: Collection<VirtualFile>? = null,
                                       val rootsUrls: IndexingUrlRootHolder? = null) : IndexableIteratorBuilder

internal object InheritedSdkIteratorBuilder : IndexableIteratorBuilder

internal data class FullModuleContentIteratorBuilder(val moduleId: ModuleId) : IndexableIteratorBuilder

internal class ModuleRootsFileBasedIteratorBuilder(val moduleId: ModuleId, val files: IndexingUrlRootHolder) : IndexableIteratorBuilder

internal data class SyntheticLibraryIteratorBuilder(val syntheticLibrary: SyntheticLibrary,
                                                    val name: String?,
                                                    val roots: Collection<VirtualFile>) : IndexableIteratorBuilder

internal data class IndexableSetContributorFilesIteratorBuilder(val name: String?,
                                                                val debugName: String,
                                                                val providedRootsToIndex: Set<VirtualFile>,
                                                                val projectAware: Boolean,
                                                                val contributor: IndexableSetContributor) : IndexableIteratorBuilder

internal data class GenericContentEntityBuilder<E : WorkspaceEntity>(val entityPointer: EntityPointer<E>,
                                                                     val roots: IndexingUrlRootHolder) : IndexableIteratorBuilder

internal data class ExternalEntityIteratorBuilder<E : WorkspaceEntity>(val entityPointer: EntityPointer<E>,
                                                                       val roots: IndexingUrlSourceRootHolder) : IndexableIteratorBuilder

internal data class CustomKindEntityBuilder<E : WorkspaceEntity>(val entityPointer: EntityPointer<E>,
                                                                 val roots: IndexingUrlRootHolder) : IndexableIteratorBuilder