// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IndexingRootsCollectionUtil")

package com.intellij.util.indexing.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import com.intellij.util.indexing.ReincludedRootsUtil
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createExternalEntityIterators
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createModuleUnawareContentEntityIterators
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl.Companion.createIterator
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forExternalEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forLibraryEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forModuleRootsFileBased
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forModuleUnawareContentEntity
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.core.fileIndex.impl.LibraryRootFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleContentOrSourceRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.virtualFile
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import java.util.function.Consumer

/**
 * Usually it makes sense to deduplicate roots, for content root and source roots may share them.
 * But there may be too many of them, resulting in a freeze, especially for Rider or CLion who add each file as a root.
 */
private const val ROOTS_SIZE_OPTIMISING_LIMIT = 1000

fun optimizeRoots(roots: Collection<VirtualFile>): List<VirtualFile> {
  val size = roots.size
  return if (size == 0) {
    emptyList()
  }
  else if (size == 1) {
    SmartList(roots.iterator().next())
  }
  else if (size > ROOTS_SIZE_OPTIMISING_LIMIT) {
    java.util.ArrayList(roots)
  }
  else {
    val filteredList: MutableList<VirtualFile> = java.util.ArrayList()
    val consumer: Consumer<VirtualFile> = object : Consumer<VirtualFile> {
      private var previousPath: String? = null
      override fun accept(file: VirtualFile) {
        val path = file.path
        if (previousPath == null || !FileUtil.startsWith(path, previousPath!!)) {
          filteredList.add(file)
          previousPath = path
        }
      }
    }
    roots.sortedWith { o1: VirtualFile, o2: VirtualFile ->
      StringUtil.compare(o1.path, o2.path, false)
    }.forEach(consumer)

    return filteredList
  }
}

internal sealed interface IndexingRootsDescription {
  fun createBuilders(): Collection<IndexableIteratorBuilder>
}

internal data class LibraryRootsDescription(val library: LibraryEntity,
                                            val classRoots: List<VirtualFile>,
                                            val sourceRoots: List<VirtualFile>) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forLibraryEntity(library.symbolicId, false, classRoots, sourceRoots)
  }
}

internal data class EntityContentRootsDescription(val entityReference: EntityReference<*>,
                                                  val roots: Collection<VirtualFile>) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forModuleUnawareContentEntity(entityReference, roots)
  }
}

internal data class EntityExternalRootsDescription(val entityReference: EntityReference<*>,
                                                   val roots: Collection<VirtualFile>,
                                                   val sourceRoots: Collection<VirtualFile>) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forExternalEntity(entityReference, roots, sourceRoots)
  }
}

private fun <T> toList(value: Collection<T>): List<T> {
  if (value is List<T>) return value
  return if (value.isEmpty()) emptyList() else ArrayList(value)
}

internal class WorkspaceIndexingRootsBuilder {
  private val moduleRoots: MultiMap<Module, VirtualFile> = MultiMap.create()
  private val descriptions: MutableCollection<IndexingRootsDescription> = mutableListOf()
  private val reincludedRoots: MutableCollection<VirtualFile> = HashSet()

  fun <E : WorkspaceEntity> registerAddedEntity(entity: E, contributor: WorkspaceFileIndexContributor<E>, storage: EntityStorage) {
    val rootData = rootData(contributor, entity, storage)
    rootData.cleanExcludedRoots()
    loadRegistrarData(rootData)
  }

  private fun <E : WorkspaceEntity> rootData(contributor: WorkspaceFileIndexContributor<E>,
                                             entity: E,
                                             storage: EntityStorage): RootData {
    val registrar = MyWorkspaceFileSetRegistrar(contributor)
    contributor.registerFileSets(entity, registrar, storage)
    return registrar.rootData
  }

  fun <E : WorkspaceEntity> registerRemovedEntity(entity: E, contributor: WorkspaceFileIndexContributor<E>, storage: EntityStorage) {
    val rootData = rootData(contributor, entity, storage)
    rootData.cleanIncludedRoots()
    loadRegistrarData(rootData)
  }

  fun <E : WorkspaceEntity> registerChangedEntity(oldEntity: E,
                                                  newEntity: E,
                                                  contributor: WorkspaceFileIndexContributor<E>,
                                                  storage: EntityStorage) {
    val oldRootData = rootData(contributor, oldEntity, storage)
    val newRootData = rootData(contributor, newEntity, storage)

    val data = RootData()
    data.moduleContents.putAllValues(diff(oldRootData.moduleContents, newRootData.moduleContents))
    data.contentRoots.putAllValues(diff(oldRootData.contentRoots, newRootData.contentRoots))
    data.libraryRoots.putAllValues(diff(oldRootData.libraryRoots, newRootData.libraryRoots))
    data.librarySourceRoots.putAllValues(diff(oldRootData.librarySourceRoots, newRootData.librarySourceRoots))
    data.externalRoots.putAllValues(diff(oldRootData.externalRoots, newRootData.externalRoots))
    data.externalSourceRoots.putAllValues(diff(oldRootData.externalSourceRoots, newRootData.externalSourceRoots))
    data.excludedRoots.addAll(oldRootData.excludedRoots)
    data.excludedRoots.removeAll(newRootData.excludedRoots)
    loadRegistrarData(data)
  }


  private fun <K : Any, V> diff(old: MultiMap<K, V>, new: MultiMap<K, V>): MultiMap<K, V> {
    val result: MultiMap<K, V> = MultiMap()
    result.putAllValues(new)
    for ((key, values) in old.entrySet()) {
      for (value in values) {
        result.remove(key, value)
      }
    }
    return result
  }

  private fun loadRegistrarData(rootData: RootData) {
    moduleRoots.putAllValues(rootData.moduleContents)

    for (entry in rootData.contentRoots.entrySet()) {
      descriptions.add(EntityContentRootsDescription(entry.key, entry.value))
    }

    for ((libraryEntity, roots) in rootData.libraryRoots.entrySet()) {
      val sourceRoots = (rootData.librarySourceRoots.remove(libraryEntity)) ?: emptyList()
      descriptions.add(LibraryRootsDescription(libraryEntity, toList(roots), toList(sourceRoots)))
    }
    for ((key, value) in rootData.librarySourceRoots.entrySet()) {
      descriptions.add(LibraryRootsDescription(key, emptyList(), toList(value)))
    }

    for ((rootEntity, roots) in rootData.externalRoots.entrySet()) {
      var sourceRoots = rootData.externalSourceRoots.remove(rootEntity)
      sourceRoots = sourceRoots ?: emptyList()
      descriptions.add(EntityExternalRootsDescription(rootEntity, roots, sourceRoots))
    }
    for ((rootEntity, roots) in rootData.externalSourceRoots.entrySet()) {
      descriptions.add(EntityExternalRootsDescription(rootEntity, emptyList(), roots))
    }

    reincludedRoots.addAll(rootData.excludedRoots)
  }

  fun createBuilders(project: Project): Collection<IndexableIteratorBuilder> {
    val builders = mutableListOf<IndexableIteratorBuilder>()
    for (entry in moduleRoots.entrySet()) {
      builders.addAll(forModuleRootsFileBased((entry.key as ModuleBridge).moduleEntityId, entry.value))
    }

    for (description in descriptions) {
      builders.addAll(description.createBuilders())
    }

    builders.addAll(ReincludedRootsUtil.createBuildersForReincludedFiles(project, reincludedRoots))
    return builders
  }

  fun addIteratorsFromRoots(iterators: MutableList<IndexableFilesIterator>,
                            libraryOriginsToFilterDuplicates: MutableSet<IndexableSetOrigin>,
                            storage: EntityStorage) {
    val initialIterators = java.util.ArrayList<IndexableFilesIterator>()
    for ((module, roots) in moduleRoots.entrySet()) {
      initialIterators.add(ModuleIndexableFilesIteratorImpl(module, toList(roots), true))
    }
    for (description in descriptions) {
      when (description) {
        is EntityContentRootsDescription -> iterators.addAll(createModuleUnawareContentEntityIterators(description.entityReference,
                                                                                                       description.roots))
        is LibraryRootsDescription -> {
          val library = description.library.findLibraryBridge(storage) ?: continue
          val iterator = createIterator(library, description.classRoots, description.sourceRoots)
          if (iterator != null && libraryOriginsToFilterDuplicates.add(iterator.origin)) {
            initialIterators.add(iterator)
          }
        }
        is EntityExternalRootsDescription -> iterators.addAll(
          createExternalEntityIterators(description.entityReference, description.roots,
                                        description.sourceRoots))
      }
    }
  }

  fun <E : WorkspaceEntity> registerEntitiesFromContributor(contributor: WorkspaceFileIndexContributor<E>,
                                                            entityStorage: EntityStorage) {
    entityStorage.entities(contributor.entityClass).forEach { entity ->
      registerAddedEntity(entity, contributor, entityStorage)
    }
  }

  fun forEachModuleContentEntitiesRoots(consumer: Consumer<Collection<VirtualFile>>) {
    for (entry in moduleRoots.entrySet()) {
      consumer.accept(entry.value)
    }
  }

  fun forEachContentEntitiesRoots(consumer: Consumer<Collection<VirtualFile>>) {
    for (description in descriptions) {
      if (description is EntityContentRootsDescription) {
        consumer.accept(description.roots)
      }
    }
  }

  fun forEachExternalEntitiesRoots(rootsConsumer: Consumer<Collection<VirtualFile>>,
                                   sourceRootsConsumer: Consumer<Collection<VirtualFile>>) {
    for (description in descriptions) {
      if (description is EntityExternalRootsDescription) {
        rootsConsumer.accept(description.roots)
        sourceRootsConsumer.accept(description.sourceRoots)
      }
    }
  }

  companion object {
    fun registerEntitiesFromContributors(project: Project,
                                         entityStorage: EntityStorage,
                                         settings: Settings?): WorkspaceIndexingRootsBuilder {
      val builder = WorkspaceIndexingRootsBuilder()
      val contributors = (WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexImpl).contributors
      for (contributor in contributors) {
        ProgressManager.checkCanceled()
        if (settings?.shouldIgnore(contributor) == true) {
          continue
        }
        builder.registerEntitiesFromContributor(contributor, entityStorage)
      }
      return builder
    }

    internal class Settings {
      var retainCondition: Condition<WorkspaceFileIndexContributor<*>>? = null
      fun shouldIgnore(contributor: WorkspaceFileIndexContributor<*>): Boolean {
        val condition = retainCondition
        return condition != null && !condition.value(contributor)
      }
    }
  }
}

private class RootData {
  val moduleContents = MultiMap.create<Module, VirtualFile>()
  val contentRoots = MultiMap.createSet<EntityReference<*>, VirtualFile>()
  val libraryRoots = MultiMap.createSet<LibraryEntity, VirtualFile>()
  val librarySourceRoots = MultiMap.createSet<LibraryEntity, VirtualFile>()
  val externalRoots = MultiMap.createSet<EntityReference<*>, VirtualFile>()
  val externalSourceRoots = MultiMap.createSet<EntityReference<*>, VirtualFile>()
  val excludedRoots = mutableListOf<VirtualFile>()

  fun registerFileSet(contributor: WorkspaceFileIndexContributor<*>,
                      root: VirtualFile,
                      kind: WorkspaceFileKind,
                      entity: WorkspaceEntity,
                      customData: WorkspaceFileSetData?) {
    if (customData is ModuleContentOrSourceRootData) {
      moduleContents.putValue(customData.module, root)
    }
    else if (kind.isContent) {
      contentRoots.putValue(entity.createReference<WorkspaceEntity>(), root)
    }
    else if (kind === WorkspaceFileKind.EXTERNAL) {
      if (contributor is LibraryRootFileIndexContributor) {
        libraryRoots.putValue(entity as LibraryEntity, root)
      }
      else {
        externalRoots.putValue(entity.createReference<WorkspaceEntity>(), root)
      }
    }
    else {
      if (contributor is LibraryRootFileIndexContributor) {
        librarySourceRoots.putValue(entity as LibraryEntity, root)
      }
      else {
        externalSourceRoots.putValue(entity.createReference<WorkspaceEntity>(), root)
      }
    }
  }

  fun registerFileSet(contributor: WorkspaceFileIndexContributor<*>,
                      root: VirtualFileUrl,
                      kind: WorkspaceFileKind,
                      entity: WorkspaceEntity,
                      customData: WorkspaceFileSetData?) {
    root.virtualFile?.let { registerFileSet(contributor, it, kind, entity, customData) }
  }

  fun registerExcludedRoot(root: VirtualFile) {
    excludedRoots.add(root)
  }

  fun registerExcludedRoot(root: VirtualFileUrl) {
    root.virtualFile?.let { excludedRoots.add(it) }
  }

  fun cleanIncludedRoots() {
    moduleContents.clear()
    contentRoots.clear()
    libraryRoots.clear()
    librarySourceRoots.clear()
    externalRoots.clear()
    externalSourceRoots.clear()
  }

  fun cleanExcludedRoots() {
    excludedRoots.clear()
  }
}

private class MyWorkspaceFileSetRegistrar(val contributor: WorkspaceFileIndexContributor<*>) : WorkspaceFileSetRegistrar {
  val rootData: RootData = RootData()

  override fun registerFileSet(root: VirtualFileUrl, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    rootData.registerFileSet(contributor, root, kind, entity, customData)
  }

  override fun registerFileSet(root: VirtualFile, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    rootData.registerFileSet(contributor, root, kind, entity, customData)
  }

  override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity) {
    rootData.registerExcludedRoot(excludedRoot)
  }

  override fun registerExcludedRoot(excludedRoot: VirtualFile, excludedFrom: WorkspaceFileKind, entity: WorkspaceEntity) {
    rootData.registerExcludedRoot(excludedRoot)
  }

  override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, excludedFrom: WorkspaceFileKind, entity: WorkspaceEntity) {
    rootData.registerExcludedRoot(excludedRoot)
  }

  override fun registerExclusionPatterns(root: VirtualFileUrl, patterns: List<String>, entity: WorkspaceEntity) {
    rootData.registerExcludedRoot(root)
  }

  override fun registerExclusionCondition(root: VirtualFileUrl, condition: (VirtualFile) -> Boolean, entity: WorkspaceEntity) {
    rootData.registerExcludedRoot(root)
  }

  override fun registerExclusionCondition(root: VirtualFile, condition: (VirtualFile) -> Boolean, entity: WorkspaceEntity) {
    rootData.registerExcludedRoot(root)
  }
}


