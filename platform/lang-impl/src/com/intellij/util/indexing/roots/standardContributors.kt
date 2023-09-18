// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.indexing.AdditionalIndexableFileSet
import com.intellij.util.indexing.IndexableFilesIndex
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.impl.PlatformInternalWorkspaceFileIndexContributor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Predicate

internal class DefaultProjectIndexableFilesContributor : IndexableFilesContributor {
  override fun getIndexableFiles(project: Project): List<IndexableFilesIterator> {
    assert(!IndexableFilesIndex.isEnabled()) { "Shouldn't be used with IndexableFilesIndex fully enabled" }
    val providers: List<IndexableFilesIterator>
    val builders: MutableList<IndexableEntityProvider.IndexableIteratorBuilder> = mutableListOf()
    val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
    for (provider in IndexableEntityProvider.EP_NAME.extensionList) {
      if (provider is IndexableEntityProvider.Existing) {
        addIteratorBuildersFromProvider(provider, entityStorage, project, builders)
        ProgressManager.checkCanceled()
      }
    }
    providers = IndexableIteratorBuilders.instantiateBuilders(builders, project, entityStorage)
    if (DependenciesIndexedStatusService.shouldBeUsed()) {
      val cacheService = DependenciesIndexedStatusService.getInstance(project)
      if (cacheService.shouldSaveStatus()) {
        cacheService.saveExcludePolicies()
      }
    }
    return providers
  }

  override fun getOwnFilePredicate(project: Project): Predicate<VirtualFile> {
    val projectFileIndex: ProjectFileIndex = ProjectFileIndex.getInstance(project)
    val indexableFilesIndex: IndexableFilesIndex? = if (IndexableFilesIndex.isEnabled())
      IndexableFilesIndex.getInstance(project)
    else null

    return Predicate {
      if (LightEdit.owns(project)) {
        return@Predicate false
      }

      if (indexableFilesIndex != null) {
        return@Predicate indexableFilesIndex.shouldBeIndexed(it)
      }

      return@Predicate if (projectFileIndex.isInContent(it) || projectFileIndex.isInLibrary(it)) {
        !FileTypeManager.getInstance().isFileIgnored(it)
      }
      else false
    }
  }

  companion object {
    private fun <E : WorkspaceEntity> addIteratorBuildersFromProvider(provider: IndexableEntityProvider.Existing<E>,
                                                                      entityStorage: EntityStorage,
                                                                      project: Project,
                                                                      iterators: MutableList<IndexableEntityProvider.IndexableIteratorBuilder>) {
      val entityClass = provider.entityClass
      for (entity in entityStorage.entities(entityClass)) {
        iterators.addAll(provider.getExistingEntityIteratorBuilder(entity, project))
      }
    }
  }
}

internal class AdditionalFilesContributor : IndexableFilesContributor {
  override fun getIndexableFiles(project: Project): List<IndexableFilesIterator> {
    if (DependenciesIndexedStatusService.shouldBeUsed()) {
      val cacheService = DependenciesIndexedStatusService.getInstance(project)
      if (cacheService.shouldSaveStatus()) {
        return cacheService.saveIndexableSetsAndInstantiateIterators()
      }
    }
    return IndexableSetContributor.EP_NAME.extensionList.flatMap {
      listOf(IndexableSetContributorFilesIterator(it, project),
             IndexableSetContributorFilesIterator(it))
    }
  }

  override fun getOwnFilePredicate(project: Project): Predicate<VirtualFile> {
    val additionalFilesContributor = AdditionalIndexableFileSet(project)
    return Predicate(additionalFilesContributor::isInSet)
  }
}

internal class AdditionalLibraryRootsContributor : IndexableFilesContributor {
  override fun getIndexableFiles(project: Project): List<IndexableFilesIterator> {
    if (DependenciesIndexedStatusService.shouldBeUsed()) {
      val cacheService = DependenciesIndexedStatusService.getInstance(project)
      if (cacheService.shouldSaveStatus()) {
        return cacheService.saveLibsAndInstantiateLibraryIterators()
      }
    }
    return AdditionalLibraryRootsProvider.EP_NAME
      .extensionList
      .flatMap { it.getAdditionalProjectLibraries(project) }
      .map { SyntheticLibraryIndexableFilesIteratorImpl(it) }
  }

  override fun getOwnFilePredicate(project: Project): Predicate<VirtualFile> {
    return Predicate { false }
    // todo: synthetic library changes are served in DefaultProjectIndexableFilesContributor
  }

  companion object {
    @JvmStatic
    fun createIndexingIterator(presentableLibraryName: @Nls String?,
                               rootsToIndex: List<VirtualFile>,
                               libraryNameForDebug: String): IndexableFilesIterator =
      AdditionalLibraryIndexableAddedFilesIterator(presentableLibraryName, rootsToIndex, libraryNameForDebug)
  }
}

internal class WorkspaceFileIndexContributorBasedContributor : IndexableFilesContributor {
  override fun getIndexableFiles(project: Project): List<IndexableFilesIterator> {
    assert(!IndexableFilesIndex.isEnabled()) { "Shouldn't be used with IndexableFilesIndex fully enabled" }

    val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
    val settings = WorkspaceIndexingRootsBuilder.Companion.Settings()
    settings.retainCondition = Condition { contributor -> contributor.storageKind == EntityStorageKind.MAIN &&
                                                          contributor !is PlatformInternalWorkspaceFileIndexContributor }
    val builder = WorkspaceIndexingRootsBuilder.registerEntitiesFromContributors(entityStorage, settings)
    val result = mutableListOf<IndexableFilesIterator>()
    builder.addIteratorsFromRoots(result, mutableSetOf(), entityStorage)
    return result
  }

  override fun getOwnFilePredicate(project: Project): Predicate<VirtualFile> {
    return Predicate { false }
    //served in DefaultProjectIndexableFilesContributor
  }
}

/**
 * Registry property introduced to provide quick workaround for possible performance issues.
 * To be removed when the feature becomes stable.
 * It's `true` by default in all the IDEs (for Rider it's enabled since 2023.2).
 */
@ApiStatus.Internal
@ApiStatus.Experimental
@Deprecated("Please don't use it", ReplaceWith("true"))
fun shouldIndexProjectBasedOnIndexableEntityProviders(): Boolean = true