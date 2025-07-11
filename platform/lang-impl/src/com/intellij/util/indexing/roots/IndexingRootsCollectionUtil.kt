// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IndexingRootsCollectionUtil")

package com.intellij.util.indexing.roots

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.indexing.ReincludedRootsUtil
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createCustomKindEntityIterators
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createGenericContentEntityIterators
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl.Companion.createIterator
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forCustomKindEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forExternalEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forGenericContentEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forLibraryEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forModuleRootsFileBased
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forSdkEntity
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.origin.*
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.core.fileIndex.impl.LibraryRootFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.SdkEntityFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.sdkMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import org.jetbrains.jps.util.JpsPathUtil
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

internal sealed interface IndexingRootsDescription {
  fun createBuilders(): Collection<IndexableIteratorBuilder>
  fun createIterator(storage: EntityStorage): IndexableFilesIterator?
}

internal interface DescriptionWithDependency: IndexingRootsDescription {
  fun hasDependency(moduleDependencyIndex: ModuleDependencyIndex): Boolean
}

internal data class SdkRootsDescription(val sdk: SdkEntity,
                                         val roots: IndexingUrlRootHolder) : DescriptionWithDependency {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forSdkEntity(sdk.symbolicId, roots)
  }

  override fun hasDependency(moduleDependencyIndex: ModuleDependencyIndex): Boolean {
    return moduleDependencyIndex.hasDependencyOn(sdk.symbolicId)
  }

  override fun createIterator(storage: EntityStorage): IndexableFilesIterator? {
    val sdk = storage.sdkMap.getDataByEntity(sdk) ?: return null
    val rootHolder = roots.toRootHolder()
    return SdkIndexableFilesIteratorImpl.createIterator(sdk, rootHolder.roots)
  }
}

internal data class LibraryRootsDescription(val library: LibraryEntity,
                                            val roots: IndexingSourceRootHolder) : DescriptionWithDependency {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forLibraryEntity(library.symbolicId, true, roots.roots, roots.sourceRoots)
  }

  override fun hasDependency(moduleDependencyIndex: ModuleDependencyIndex): Boolean {
    return moduleDependencyIndex.hasDependencyOn(library.symbolicId)
  }

  override fun createIterator(storage: EntityStorage): IndexableFilesIterator? {
    val library = library.findLibraryBridge(storage) ?: return null
    return createIterator(library, roots.roots, roots.sourceRoots)
  }
}

internal data class LibraryUrlRootsDescription(val library: LibraryEntity,
                                               val roots: IndexingUrlSourceRootHolder) : DescriptionWithDependency {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forLibraryEntity(library.symbolicId, false, roots)
  }

  override fun hasDependency(moduleDependencyIndex: ModuleDependencyIndex): Boolean {
    return moduleDependencyIndex.hasDependencyOn(library.symbolicId)
  }

  override fun createIterator(storage: EntityStorage): IndexableFilesIterator? {
    val library = library.findLibraryBridge(storage) ?: return null
    val fileRoots = roots.toSourceRootHolder()
    if (fileRoots.isEmpty()) return null
    return createIterator(library, fileRoots.roots, fileRoots.sourceRoots)
  }
}

internal data class EntityGenericContentRootsDescription<E : WorkspaceEntity>(val entityPointer: EntityPointer<E>,
                                                                              val roots: IndexingUrlRootHolder) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forGenericContentEntity(entityPointer, roots)
  }

  override fun createIterator(storage: EntityStorage): IndexableFilesIterator? {
    return createGenericContentEntityIterators(entityPointer, roots).firstOrNull()
  }
}

internal data class EntityExternalRootsDescription<E : WorkspaceEntity>(val entityPointer: EntityPointer<E>,
                                                                        val urlRoots: IndexingUrlSourceRootHolder) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forExternalEntity(entityPointer, urlRoots)
  }

  override fun createIterator(storage: EntityStorage): IndexableFilesIterator? {
    return IndexableEntityProviderMethods.createExternalEntityIterators(entityPointer, urlRoots).firstOrNull()
  }
}

internal data class EntityCustomKindRootsDescription<E : WorkspaceEntity>(val entityPointer: EntityPointer<E>,
                                                                          val roots: IndexingUrlRootHolder) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forCustomKindEntity(entityPointer, roots)
  }

  override fun createIterator(storage: EntityStorage): IndexableFilesIterator? {
    return createCustomKindEntityIterators(entityPointer, roots).firstOrNull()
  }
}

internal fun selectRootVirtualFiles(value: Collection<VirtualFile>): List<VirtualFile> {
  return selectRootItems(value) { file -> file.path }
}

internal fun selectRootVirtualFileUrls(urls: Collection<VirtualFileUrl>): List<VirtualFileUrl> {
  return selectRootItems(urls) { url -> JpsPathUtil.urlToPath(url.url) }
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
  private val reincludedRoots: MutableCollection<VirtualFile> = HashSet()

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

    val data = RootData(contributor, ignoreModuleRoots)
    addDiff(data.moduleContents, oldRootData.moduleContents, newRootData.moduleContents)
    addDiff(data.customizedModuleContentRoots, oldRootData.customizedModuleContentRoots, newRootData.customizedModuleContentRoots)
    addDiff(data.contentRoots, oldRootData.contentRoots, newRootData.contentRoots)
    addUrlSourceDiff(data.libraryUrlRoots, oldRootData.libraryUrlRoots, newRootData.libraryUrlRoots)
    addDiff(data.sdkRoots, oldRootData.sdkRoots, newRootData.sdkRoots)
    addSourceDiff(data.libraryRoots, oldRootData.libraryRoots, newRootData.libraryRoots)
    addUrlSourceDiff(data.externalRoots, oldRootData.externalRoots, newRootData.externalRoots)
    data.excludedRoots.addAll(oldRootData.excludedRoots)
    data.excludedRoots.removeAll(newRootData.excludedRoots)
    loadRegistrarData(data)
  }

  private fun <K> addDiff(base: MutableMap<K, MutableIndexingUrlRootHolder>,
                          oldData: MutableMap<K, MutableIndexingUrlRootHolder>,
                          newData: MutableMap<K, MutableIndexingUrlRootHolder>) {
    newData.entries.forEach { entry ->
      entry.value.remove(oldData[entry.key])
      base.getOrPut(entry.key) { MutableIndexingUrlRootHolder() }.addRoots(entry.value)
    }
  }

  private fun <K> addSourceDiff(base: MutableMap<K, MutableIndexingSourceRootHolder>,
                                oldData: MutableMap<K, MutableIndexingSourceRootHolder>,
                                newData: MutableMap<K, MutableIndexingSourceRootHolder>) {
    newData.entries.forEach { entry ->
      entry.value.remove(oldData[entry.key])
      base.getOrPut(entry.key) { MutableIndexingSourceRootHolder() }.addRoots(entry.value)
    }
  }

  private fun <K> addUrlSourceDiff(base: MutableMap<K, MutableIndexingUrlSourceRootHolder>,
                                   oldData: MutableMap<K, MutableIndexingUrlSourceRootHolder>,
                                   newData: MutableMap<K, MutableIndexingUrlSourceRootHolder>) {
    newData.entries.forEach { entry ->
      entry.value.remove(oldData[entry.key])
      base.getOrPut(entry.key) { MutableIndexingUrlSourceRootHolder() }.addRoots(entry.value)
    }
  }

  private fun <E : WorkspaceEntity> loadRegistrarData(rootData: RootData<E>) {
    rootData.moduleContents.entries.forEach { entry ->
      moduleRoots.getOrPut(entry.key) { MutableIndexingUrlRootHolder() }.addRoots(entry.value)
    }

    for (entry in rootData.contentRoots.entries) {
      descriptions.add(EntityGenericContentRootsDescription(entry.key, entry.value))
    }

    for ((libraryEntity, roots) in rootData.libraryRoots.entries) {
      descriptions.add(LibraryRootsDescription(libraryEntity, roots))
    }

    for ((libraryEntity, roots) in rootData.libraryUrlRoots.entries) {
      descriptions.add(LibraryUrlRootsDescription(libraryEntity, roots))
    }

    for ((entityReference, roots) in rootData.externalRoots.entries) {
      descriptions.add(EntityExternalRootsDescription(entityReference, roots))
    }

    for ((entityReference, roots) in rootData.customKindRoots.entries) {
      descriptions.add(EntityCustomKindRootsDescription(entityReference, roots))
    }
    for ((sdkEntity, roots) in rootData.sdkRoots.entries) {
      descriptions.add(SdkRootsDescription(sdkEntity, roots))
    }
    reincludedRoots.addAll(rootData.excludedRoots)
  }

  fun createBuilders(project: Project): Collection<IndexableIteratorBuilder> {
    val builders = mutableListOf<IndexableIteratorBuilder>()
    for (entry in moduleRoots.entries) {
      builders.addAll(forModuleRootsFileBased((entry.key as ModuleBridge).moduleEntityId, entry.value))
    }

    processDescriptions(project) { description ->
      builders.addAll(description.createBuilders())
    }

    builders.addAll(ReincludedRootsUtil.createBuildersForReincludedFiles(project, reincludedRoots))
    return builders
  }

  fun getIteratorsFromRoots(storage: EntityStorage,
                            project: Project,
                            libraryOrigins: MutableSet<IndexableSetOrigin>): List<IndexableFilesIterator> {
    val result = mutableListOf<IndexableFilesIterator>()
    for ((module, roots) in moduleRoots.entries) {
      result.addAll(IndexableEntityProviderMethods.createIterators(module, roots))
    }
    processDescriptions(project) { description ->
      val iterator = description.createIterator(storage)
      if (iterator == null) return@processDescriptions
      if (iterator is LibraryIndexableFilesIterator) {
        if (libraryOrigins.add(iterator.origin)) {
          result.add(iterator)
        }
      } else {
        result.add(iterator)
      }
    }
    return result
  }

  private fun processDescriptions(project: Project,
                                  processor: (IndexingRootsDescription) -> Unit) {
    val moduleDependencyIndex = ModuleDependencyIndex.getInstance(project)
    for (description in descriptions) {
      when (description) {
        is DescriptionWithDependency -> {
          if (description.hasDependency(moduleDependencyIndex)) {
            processor(description)
          }
        }
        else -> processor(description)
      }
    }
  }

  fun <E : WorkspaceEntity> registerEntitiesFromContributor(contributor: WorkspaceFileIndexContributor<E>,
                                                            entityStorage: EntityStorage) {
    entityStorage.entities(contributor.entityClass).forEach { entity ->
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

  companion object {
    @JvmOverloads
    fun registerEntitiesFromContributors(entityStorage: EntityStorage,
                                         settings: Settings = Settings.DEFAULT): WorkspaceIndexingRootsBuilder {
      val builder = WorkspaceIndexingRootsBuilder(!settings.collectExplicitRootsForModules)
      for (contributor in WorkspaceFileIndexImpl.EP_NAME.extensionList) {
        ProgressManager.checkCanceled()
        if (settings.shouldIgnore(contributor)) {
          continue
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

private class RootData<E : WorkspaceEntity>(val contributor: WorkspaceFileIndexContributor<E>,
                                            val ignoreModuleRoots: Boolean) {
  val moduleContents = mutableMapOf<Module, MutableIndexingUrlRootHolder>()
  val customizedModuleContentRoots = mutableMapOf<EntityPointer<E>, MutableIndexingUrlRootHolder>()
  val contentRoots = mutableMapOf<EntityPointer<E>, MutableIndexingUrlRootHolder>()
  val libraryUrlRoots = mutableMapOf<LibraryEntity, MutableIndexingUrlSourceRootHolder>()
  val libraryRoots = mutableMapOf<LibraryEntity, MutableIndexingSourceRootHolder>()
  val sdkRoots = mutableMapOf<SdkEntity, MutableIndexingUrlRootHolder>()
  val externalRoots = mutableMapOf<EntityPointer<E>, MutableIndexingUrlSourceRootHolder>()
  val customKindRoots = mutableMapOf<EntityPointer<E>, MutableIndexingUrlRootHolder>()
  val excludedRoots = mutableListOf<VirtualFile>()

  fun registerFileSet(root: VirtualFileUrl,
                      kind: WorkspaceFileKind,
                      entity: E,
                      customData: WorkspaceFileSetData?,
                      recursive: Boolean) {
    if (!kind.isIndexable) return

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
      if (!ignoreModuleRoots) {
        addRoot(moduleContents, customData.module)
      }
    }
    else if (kind.isContent) {
      addRoot(contentRoots, entityReference)
    }
    else if (contributor is SdkEntityFileIndexContributor) {
      addRoot(sdkRoots, entity as SdkEntity)
    }
    else if (contributor is LibraryRootFileIndexContributor) {
      addRoot(libraryUrlRoots, entity as LibraryEntity, kind === WorkspaceFileKind.EXTERNAL_SOURCE)
    }
    else if (kind == WorkspaceFileKind.CUSTOM) {
      addRoot(customKindRoots, entityReference)
    }
    else {
      addRoot(externalRoots, entityReference, kind === WorkspaceFileKind.EXTERNAL_SOURCE)
    }
  }

  fun registerFileSet(root: VirtualFile,
                      kind: WorkspaceFileKind,
                      entity: WorkspaceEntity,
                      recursive: Boolean) {
    if (!kind.isIndexable) return

    thisLogger().assertTrue(contributor is LibraryRootFileIndexContributor,
                            "Registering VirtualFile roots is not supported, register VirtualFileUrl from $contributor instead")
    if (contributor !is LibraryRootFileIndexContributor) {
      return
    }
    val holder = libraryRoots.getOrPut(entity as LibraryEntity) { MutableIndexingSourceRootHolder() }
    if (kind === WorkspaceFileKind.EXTERNAL_SOURCE) {
      (if (recursive) holder.sourceRoots else holder.nonRecursiveSourceRoots).add(root)
    }
    else {
      (if (recursive) holder.roots else holder.nonRecursiveRoots).add(root)
    }
  }

  fun registerExcludedRoot(root: VirtualFileUrl) {
    root.virtualFile?.let { excludedRoots.add(it) }
  }

  fun cleanIncludedRoots() {
    moduleContents.clear()
    customizedModuleContentRoots.clear()
    contentRoots.clear()
    libraryUrlRoots.clear()
    libraryRoots.clear()
    sdkRoots.clear()
    externalRoots.clear()
    customKindRoots.clear()
  }

  fun cleanExcludedRoots() {
    excludedRoots.clear()
  }
}

private class MyWorkspaceFileSetRegistrar<E : WorkspaceEntity>(contributor: WorkspaceFileIndexContributor<E>,
                                                               ignoreModuleRoots: Boolean) : WorkspaceFileSetRegistrar {
  val rootData: RootData<E> = RootData(contributor, ignoreModuleRoots)

  override fun registerFileSet(root: VirtualFileUrl, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    @Suppress("UNCHECKED_CAST")
    rootData.registerFileSet(root, kind, entity as E, customData, true)
  }

  override fun registerFileSet(root: VirtualFile, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    rootData.registerFileSet(root, kind, entity, true)
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

  override fun registerExclusionCondition(root: VirtualFileUrl, condition: (VirtualFile) -> Boolean, entity: WorkspaceEntity) {
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


