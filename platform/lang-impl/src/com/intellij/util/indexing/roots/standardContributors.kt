// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.indexing.AdditionalIndexableFileSet
import com.intellij.util.indexing.IndexableFilesIndex
import com.intellij.util.indexing.IndexableSetContributor
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders
import com.intellij.workspaceModel.core.fileIndex.impl.PlatformInternalWorkspaceFileIndexContributor
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Predicate

internal class DefaultProjectIndexableFilesContributor : IndexableFilesContributor {
  override fun getIndexableFiles(project: Project): List<IndexableFilesIterator> {
    assert(!IndexableFilesIndex.isEnabled()) { "Shouldn't be used with IndexableFilesIndex fully enabled" }
    val providers: List<IndexableFilesIterator>
    if (shouldIndexProjectBasedOnIndexableEntityProviders()) {
      val builders: MutableList<IndexableEntityProvider.IndexableIteratorBuilder> = mutableListOf()
      val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
      for (provider in IndexableEntityProvider.EP_NAME.extensionList) {
        if (provider is IndexableEntityProvider.Existing) {
          addIteratorBuildersFromProvider(provider, entityStorage, project, builders)
          ProgressManager.checkCanceled()
        }
      }
      providers = IndexableIteratorBuilders.instantiateBuilders(builders, project, entityStorage)
    }
    else {
      val seenLibraries: MutableSet<Library> = HashSet()
      val seenSdks: MutableSet<Sdk> = HashSet()
      val modules = ModuleManager.getInstance(project).sortedModules

      val providersCollection: MutableList<IndexableFilesIterator> = mutableListOf()
      for (module in modules) {
        providersCollection.addAll(ModuleIndexableFilesIteratorImpl.getModuleIterators(module))

        val orderEntries = ModuleRootManager.getInstance(module).orderEntries
        for (orderEntry in orderEntries) {
          when (orderEntry) {
            is LibraryOrderEntry -> {
              val library = orderEntry.library
              if (library != null && seenLibraries.add(library)) {
                providersCollection.addIfNotNull(LibraryIndexableFilesIteratorImpl.createIterator(library))
              }
            }
            is JdkOrderEntry -> {
              val sdk = orderEntry.jdk
              if (sdk != null && seenSdks.add(sdk)) {
                providersCollection.add(SdkIndexableFilesIteratorImpl.createIterator(sdk))
              }
            }
          }
        }
      }
      providers = providersCollection
    }
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
    if (!shouldIndexProjectBasedOnIndexableEntityProviders()) {
      return emptyList()
    }
    assert(!IndexableFilesIndex.isEnabled()) { "Shouldn't be used with IndexableFilesIndex fully enabled" }

    val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
    val settings = WorkspaceIndexingRootsBuilder.Companion.Settings()
    settings.retainCondition = Condition { contributor -> contributor !is PlatformInternalWorkspaceFileIndexContributor }
    val builder = WorkspaceIndexingRootsBuilder.registerEntitiesFromContributors(project, entityStorage, settings)
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
 * It's `true` by default in all the IDEs except for Rider. Rider plans to enable it in 2023.1.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun shouldIndexProjectBasedOnIndexableEntityProviders(): Boolean = Registry.`is`("indexing.enable.entity.provider.based.indexing")