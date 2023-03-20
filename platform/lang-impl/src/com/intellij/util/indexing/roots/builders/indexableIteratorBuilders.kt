// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.IndexingContributorCustomization
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

internal object IndexableIteratorBuilders {
  private val logger = thisLogger()

  fun forModuleRoots(moduleId: ModuleId, urls: Collection<VirtualFileUrl>): Collection<IndexableIteratorBuilder> =
    if (urls.isEmpty()) emptyList() else listOf(ModuleRootsIteratorBuilder(moduleId, urls))

  fun forModuleRootsFileBased(moduleId: ModuleId, files: Collection<VirtualFile>): Collection<IndexableIteratorBuilder> =
    if (files.isEmpty()) emptyList() else listOf(ModuleRootsFileBasedIteratorBuilder(moduleId, files))

  fun forModuleRoots(moduleId: ModuleId, url: VirtualFileUrl): Collection<IndexableIteratorBuilder> =
    listOf(ModuleRootsIteratorBuilder(moduleId, url))

  fun forModuleRoots(moduleId: ModuleId,
                     newRoots: List<VirtualFileUrl>,
                     oldRoots: List<VirtualFileUrl>): Collection<IndexableIteratorBuilder> {
    val roots = newRoots.toMutableList()
    roots.removeAll(oldRoots)
    return forModuleRoots(moduleId, roots)
  }

  @JvmOverloads
  fun forLibraryEntity(libraryId: LibraryId,
                       dependencyChecked: Boolean,
                       roots: Collection<VirtualFile>? = null,
                       sourceRoots: Collection<VirtualFile>? = null): Collection<IndexableIteratorBuilder> =
    listOf(LibraryIdIteratorBuilder(libraryId, roots, sourceRoots, dependencyChecked))

  fun forSdk(sdkName: String, sdkType: String): Collection<IndexableIteratorBuilder> = listOf(SdkIteratorBuilder(sdkName, sdkType))

  fun forSdk(sdk: Sdk, file: VirtualFile): Collection<IndexableIteratorBuilder> = forSdk(sdk, listOf(file))

  fun forSdk(sdk: Sdk, files: Collection<VirtualFile>): Collection<IndexableIteratorBuilder> = listOf(SdkIteratorBuilder(sdk, files))

  fun forInheritedSdk(): Collection<IndexableIteratorBuilder> = listOf(InheritedSdkIteratorBuilder)

  fun forModuleContent(moduleId: ModuleId): Collection<IndexableIteratorBuilder> = listOf(FullModuleContentIteratorBuilder(moduleId))


  fun <E : WorkspaceEntity> forModuleAwareCustomizedContentEntity(moduleId: ModuleId,
                                                                  entityReference: EntityReference<E>,
                                                                  files: Collection<VirtualFile>,
                                                                  customization: IndexingContributorCustomization<E, *>): Collection<IndexableIteratorBuilder> =
    if (files.isEmpty()) emptyList() else listOf(ModuleAwareCustomizedContentEntityBuilder(moduleId, entityReference, files, customization))

  fun <E : WorkspaceEntity> forModuleUnawareContentEntity(entityReference: EntityReference<E>,
                                                          roots: Collection<VirtualFile>,
                                                          customization: IndexingContributorCustomization<E, *>?): Collection<IndexableIteratorBuilder> =
    if (roots.isEmpty()) emptyList()
    else listOf(ModuleUnawareContentEntityBuilder(entityReference, roots, customization))

  fun <E : WorkspaceEntity, D> forExternalEntity(entityReference: EntityReference<E>,
                                                 roots: Collection<VirtualFile>,
                                                 sourceRoots: Collection<VirtualFile>,
                                                 customization: IndexingContributorCustomization<E, D>?): Collection<IndexableIteratorBuilder> =
    if (roots.isEmpty() && sourceRoots.isEmpty()) emptyList()
    else listOf(ExternalEntityIteratorBuilder(entityReference, roots, sourceRoots, customization))

  fun instantiateBuilders(builders: List<IndexableIteratorBuilder>,
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
                                             val dependencyChecked: Boolean = false) : IndexableIteratorBuilder

internal data class SdkIteratorBuilder(val sdkName: String,
                                       val sdkType: String,
                                       val roots: Collection<VirtualFile>? = null) : IndexableIteratorBuilder {
  constructor(sdk: Sdk, roots: Collection<VirtualFile>) : this(sdk.name, sdk.sdkType.name, roots)
}

internal object InheritedSdkIteratorBuilder : IndexableIteratorBuilder

internal data class FullModuleContentIteratorBuilder(val moduleId: ModuleId) : IndexableIteratorBuilder

internal class ModuleRootsIteratorBuilder(val moduleId: ModuleId, val urls: Collection<VirtualFileUrl>) : IndexableIteratorBuilder {
  constructor(moduleId: ModuleId, url: VirtualFileUrl) : this(moduleId, listOf(url))
}

internal class ModuleRootsFileBasedIteratorBuilder(val moduleId: ModuleId, val files: Collection<VirtualFile>) : IndexableIteratorBuilder

internal data class SyntheticLibraryIteratorBuilder(val syntheticLibrary: SyntheticLibrary,
                                                    val name: String?,
                                                    val roots: Collection<VirtualFile>) : IndexableIteratorBuilder

internal data class IndexableSetContributorFilesIteratorBuilder(val name: String?,
                                                                val debugName: String,
                                                                val providedRootsToIndex: Set<VirtualFile>,
                                                                val projectAware: Boolean,
                                                                val contributor: IndexableSetContributor) : IndexableIteratorBuilder

internal data class ModuleAwareCustomizedContentEntityBuilder<E : WorkspaceEntity>(val moduleId: ModuleId,
                                                                                   val entityReference: EntityReference<E>,
                                                                                   val roots: Collection<VirtualFile>,
                                                                                   val customization: IndexingContributorCustomization<E, *>) : IndexableIteratorBuilder

internal data class ModuleUnawareContentEntityBuilder<E : WorkspaceEntity>(val entityReference: EntityReference<E>,
                                                                           val roots: Collection<VirtualFile>,
                                                                           val customization: IndexingContributorCustomization<E, *>?) : IndexableIteratorBuilder

internal data class ExternalEntityIteratorBuilder<E : WorkspaceEntity>(val entityReference: EntityReference<E>,
                                                                       val roots: Collection<VirtualFile>,
                                                                       val sourceRoots: Collection<VirtualFile>,
                                                                       val customization: IndexingContributorCustomization<E, *>?) : IndexableIteratorBuilder