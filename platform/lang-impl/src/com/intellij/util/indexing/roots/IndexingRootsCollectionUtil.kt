// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IndexingRootsCollectionUtil")

package com.intellij.util.indexing.roots

import com.intellij.openapi.extensions.forEachExtensionSafeInline
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder
import com.intellij.util.indexing.roots.origin.IndexingUrlSourceRootHolder
import com.intellij.util.indexing.roots.origin.LibraryOriginImpl
import com.intellij.util.indexing.roots.origin.MutableIndexingUrlRootHolder
import com.intellij.util.indexing.roots.origin.MutableIndexingUrlSourceRootHolder
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetExclusionCondition
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import java.util.NavigableMap
import java.util.TreeMap
import java.util.function.Consumer
import java.util.function.Function

internal sealed interface IndexingRootsDescription

internal data class EntityGenericContentRootsDescription<E : WorkspaceEntity>(val entityPointer: EntityPointer<E>,
                                                                              val roots: IndexingUrlRootHolder) : IndexingRootsDescription

internal data class EntityExternalRootsDescription<E : WorkspaceEntity>(val entityPointer: EntityPointer<E>,
                                                                        val urlRoots: IndexingUrlSourceRootHolder) : IndexingRootsDescription

internal fun selectRootVirtualFiles(value: Collection<VirtualFile>): List<VirtualFile> {
  return selectRootItems(value) { file -> file.path }
}

private fun <T> selectRootItems(items: Collection<T>, toPath: Function<T, String>): List<T> {
  if (items.size < 2) {
    if (items is List<T>) return items
    return if (items.isEmpty()) emptyList() else ArrayList(items)
  }

  val pathMap = TreeMap<String, T>(OSAgnosticPathUtil.COMPARATOR)
  for (item in items) {
    val path = FileUtil.toSystemIndependentName(toPath.apply(item))
    if (!isIncluded(pathMap, path)) {
      pathMap[path] = item
      while (true) {
        val excludedPath = pathMap.higherKey(path)
        if (excludedPath != null && OSAgnosticPathUtil.startsWith(excludedPath, path)) {
          pathMap.remove(excludedPath)
        }
        else {
          break
        }
      }
    }
  }
  return pathMap.values.toList()
}

private fun isIncluded(existingFiles: NavigableMap<String, *>, path: String): Boolean {
  val suggestedCoveringRoot = existingFiles.floorKey(path)
  return suggestedCoveringRoot != null && OSAgnosticPathUtil.startsWith(path, suggestedCoveringRoot)
}

internal class WorkspaceIndexingRootsBuilder(private val ignoreModuleRoots: Boolean) {
  private val moduleRoots: MutableMap<Module, MutableIndexingUrlRootHolder> = mutableMapOf()
  private val descriptions: MutableCollection<IndexingRootsDescription> = mutableListOf()
  private val nonIndexableRoots: MutableCollection<VirtualFile> = HashSet()

  fun <E : WorkspaceEntity> registerAddedEntity(entity: E,
                                                contributor: WorkspaceFileIndexContributor<E>,
                                                storage: EntityStorage) {
    val rootData = rootData(contributor, entity, storage)
    rootData.cleanExcludedRoots()
    loadRegistrarData(rootData)
  }

  private fun <E : WorkspaceEntity> rootData(contributor: WorkspaceFileIndexContributor<E>,
                                             entity: E,
                                             storage: EntityStorage): RootData<E> {
    val registrar = MyWorkspaceFileSetRegistrar(contributor, ignoreModuleRoots)
    contributor.registerFileSets(entity, registrar, storage)
    return registrar.rootData
  }

  private fun <E : WorkspaceEntity> loadRegistrarData(rootData: RootData<E>) {
    rootData.moduleContents.entries.forEach { entry ->
      moduleRoots.getOrPut(entry.key) { MutableIndexingUrlRootHolder() }.addRoots(entry.value)
    }

    for (entry in rootData.contentRoots.entries) {
      descriptions.add(EntityGenericContentRootsDescription(entry.key, entry.value))
    }

    for ((entityReference, roots) in rootData.externalRoots.entries) {
      descriptions.add(EntityExternalRootsDescription(entityReference, roots))
    }
    nonIndexableRoots.addAll(rootData.nonIndexableRoots)
  }

  fun <E : WorkspaceEntity> registerEntitiesFromContributor(contributor: WorkspaceFileIndexContributor<E>,
                                                            entityStorage: EntityStorage) {
    entityStorage.entities(contributor.entityClass).forEach { entity ->
      Cancellation.checkCancelled()
      registerAddedEntity(entity, contributor, entityStorage)
    }
  }

  fun forEachModuleContentEntitiesRoots(consumer: Consumer<IndexingUrlRootHolder>) {
    for (entry in moduleRoots.entries) {
      consumer.accept(entry.value)
    }
  }

  fun forEachContentEntitiesRoots(consumer: Consumer<IndexingUrlRootHolder>) {
    for (description in descriptions) {
      if (description is EntityGenericContentRootsDescription<*>) {
        consumer.accept(description.roots)
      }
    }
  }

  fun forEachExternalEntitiesRoots(consumer: Consumer<IndexingUrlSourceRootHolder>) {
    for (description in descriptions) {
      if (description is EntityExternalRootsDescription<*>) {
        consumer.accept(description.urlRoots)
      }
    }
  }

  fun forEachNonIndexableRoots(consumer: Consumer<Collection<VirtualFile>>) {
    consumer.accept(nonIndexableRoots)
  }

  companion object {
    @JvmOverloads
    fun registerEntitiesFromContributors(entityStorage: EntityStorage,
                                         settings: Settings = Settings.DEFAULT): WorkspaceIndexingRootsBuilder {
      val builder = WorkspaceIndexingRootsBuilder(!settings.collectExplicitRootsForModules)
      WorkspaceFileIndexImpl.EP_NAME.forEachExtensionSafeInline { contributor ->
        ProgressManager.checkCanceled()
        if (settings.shouldIgnore(contributor)) {
          return@forEachExtensionSafeInline
        }
        builder.registerEntitiesFromContributor(contributor, entityStorage)
      }
      return builder
    }

    internal class Settings {
      companion object {
        val DEFAULT: Settings = Settings()

        init {
          DEFAULT.retainCondition = Condition<WorkspaceFileIndexContributor<*>> { contributor -> contributor.storageKind == EntityStorageKind.MAIN }
        }
      }
      var retainCondition: Condition<WorkspaceFileIndexContributor<*>>? = null
      var collectExplicitRootsForModules: Boolean = true

      fun shouldIgnore(contributor: WorkspaceFileIndexContributor<*>): Boolean {
        val condition = retainCondition
        return condition != null && !condition.value(contributor)
      }
    }
  }
}

private class RootData<E : WorkspaceEntity> {
  val moduleContents = mutableMapOf<Module, MutableIndexingUrlRootHolder>()
  val contentRoots = mutableMapOf<EntityPointer<E>, MutableIndexingUrlRootHolder>()
  val externalRoots = mutableMapOf<EntityPointer<E>, MutableIndexingUrlSourceRootHolder>()
  val excludedRoots = mutableListOf<VirtualFile>()
  val nonIndexableRoots = mutableListOf<VirtualFile>()

  fun registerFileSet(root: VirtualFileUrl,
                      kind: WorkspaceFileKind,
                      entity: E,
                      customData: WorkspaceFileSetData?,
                      recursive: Boolean) {
    if (!kind.isIndexable) {
      root.virtualFile?.let { nonIndexableRoots.add(it) }
      return
    }

    val entityReference = entity.createPointer<E>()

    fun <K> addRoot(map: MutableMap<K, MutableIndexingUrlRootHolder>, key: K) {
      val holder = map.getOrPut(key) { MutableIndexingUrlRootHolder() }
      (if (recursive) holder.roots else holder.nonRecursiveRoots).add(root)
    }

    fun <K> addRoot(map: MutableMap<K, MutableIndexingUrlSourceRootHolder>, key: K, sourceRoot: Boolean) {
      val holder = map.getOrPut(key) { MutableIndexingUrlSourceRootHolder() }
      if (sourceRoot) {
        (if (recursive) holder.sourceRoots else holder.nonRecursiveSourceRoots).add(root)
      }
      else {
        (if (recursive) holder.roots else holder.nonRecursiveRoots).add(root)
      }
    }

    if (customData is ModuleRelatedRootData) {
      addRoot(moduleContents, customData.module)
    }
    else if (kind.isContent) {
      addRoot(contentRoots, entityReference)
    }
    else if (kind == WorkspaceFileKind.CUSTOM) {
      // skip
    }
    else {
      addRoot(externalRoots, entityReference, kind === WorkspaceFileKind.EXTERNAL_SOURCE)
    }
  }

  fun registerFileSet(root: VirtualFile,
                      kind: WorkspaceFileKind
  ) {
    if (!kind.isIndexable) {
      nonIndexableRoots.add(root)
      return
    }
  }

  fun registerExcludedRoot(root: VirtualFileUrl) {
    root.virtualFile?.let { excludedRoots.add(it) }
  }

  fun cleanExcludedRoots() {
    excludedRoots.clear()
  }
}

internal fun processModuleRoot(fileSet: WorkspaceFileSetWithCustomData<*>, project: Project, includeNestedRoots: Boolean = false): IndexableFilesIterator? {
  val customData = fileSet.data
  val root = fileSet.root
  customData as ModuleRelatedRootData

  return if (!includeNestedRoots && isNestedRootOfModuleContent(root, customData.module, WorkspaceFileIndexEx.getInstance(project))) {
    null
  } else {
    ModuleFilesIteratorImpl(customData.module, root, fileSet.recursive, true)
  }
}

internal fun processLibraryEntity(entity: LibraryEntity, fileSet: WorkspaceFileSet): Pair<LibraryOrigin, IndexableFilesIterator> {
  val sourceRoot = fileSet.kind == WorkspaceFileKind.EXTERNAL_SOURCE
  val origin = if (sourceRoot) {
    LibraryOriginImpl(emptyList(), listOf(fileSet.root))
  }
  else {
    LibraryOriginImpl(listOf(fileSet.root), emptyList())
  }
  val iterator = GenericDependencyIterator.forLibraryEntity(origin, entity.name, fileSet.root, sourceRoot)
  return origin to iterator
}

private fun isNestedRootOfModuleContent(root: VirtualFile, module: Module, workspaceFileIndex: WorkspaceFileIndexEx): Boolean {
  val parent = root.getParent()
  if (parent == null) {
    return false
  }
  val fileInfo = workspaceFileIndex.getFileInfo(
    parent,
    honorExclusion = false,
    includeContentSets = true,
    includeContentNonIndexableSets = true,
    includeExternalSets = false,
    includeExternalSourceSets = false,
    includeExternalNonIndexableSets = false,
    includeCustomKindSets = false
  )
  return fileInfo.findFileSet { fileSet -> hasRecursiveRootFromModuleContent(fileSet, module) } != null
}

private fun hasRecursiveRootFromModuleContent(fileSet: WorkspaceFileSetWithCustomData<*>, module: Module): Boolean {
  return fileSet.recursive && isInContent(fileSet, module)
}

private fun isInContent(fileSet: WorkspaceFileSetWithCustomData<*>, module: Module): Boolean {
  val data = fileSet.data
  return data is ModuleRelatedRootData && module == data.module
}

private class MyWorkspaceFileSetRegistrar<E : WorkspaceEntity>(contributor: WorkspaceFileIndexContributor<E>,
                                                               ignoreModuleRoots: Boolean) : WorkspaceFileSetRegistrar {
  val rootData: RootData<E> = RootData()

  override fun registerFileSet(root: VirtualFileUrl, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    @Suppress("UNCHECKED_CAST")
    rootData.registerFileSet(root, kind, entity as E, customData, true)
  }

  override fun registerFileSet(root: VirtualFile, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    rootData.registerFileSet(root, kind)
  }

  override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity) {
    rootData.registerExcludedRoot(excludedRoot)
  }

  override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, excludedFrom: WorkspaceFileKind, entity: WorkspaceEntity) {
    rootData.registerExcludedRoot(excludedRoot)
  }

  override fun registerExclusionPatterns(root: VirtualFileUrl, patterns: List<String>, entity: WorkspaceEntity) {
    rootData.registerExcludedRoot(root)
  }

  override fun registerExclusionCondition(root: VirtualFileUrl, condition: WorkspaceFileSetExclusionCondition, entity: WorkspaceEntity) {
    rootData.registerExcludedRoot(root)
  }

  override fun registerNonRecursiveFileSet(file: VirtualFileUrl,
                                           kind: WorkspaceFileKind,
                                           entity: WorkspaceEntity,
                                           customData: WorkspaceFileSetData?) {
    @Suppress("UNCHECKED_CAST")
    rootData.registerFileSet(file, kind, entity as E, customData, false)
  }
}


