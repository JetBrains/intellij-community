// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.platform.workspace.storage.EntityReference
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.containers.MultiMap
import com.intellij.util.indexing.CustomizingIndexingPresentationContributor
import com.intellij.util.indexing.ReincludedRootsUtil
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createCustomKindEntityIterators
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createGenericContentEntityIterators
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createModuleAwareContentEntityIterators
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl.Companion.createIterator
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forCustomKindEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forExternalEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forGenericContentEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forLibraryEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forModuleAwareCustomizedContentEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forModuleRootsFileBased
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.origin.IndexingRootHolder
import com.intellij.util.indexing.roots.origin.IndexingSourceRootHolder
import com.intellij.util.indexing.roots.origin.MutableIndexingRootHolder
import com.intellij.util.indexing.roots.origin.MutableIndexingSourceRootHolder
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.core.fileIndex.impl.LibraryRootFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.jps.util.JpsPathUtil
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

internal sealed interface IndexingRootsDescription {
  fun createBuilders(): Collection<IndexableIteratorBuilder>
}

internal data class LibraryRootsDescription(val library: LibraryEntity,
                                            val roots: IndexingSourceRootHolder) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forLibraryEntity(library.symbolicId, false, roots.roots, roots.sourceRoots)
  }

  fun createIterators(storage: EntityStorage): Collection<IndexableFilesIterator> {
    val library = library.findLibraryBridge(storage) ?: return emptyList()
    return listOfNotNull(createIterator(library, roots.roots, roots.sourceRoots))
  }
}

internal data class EntityContentRootsCustomizedModuleAwareDescription<E : WorkspaceEntity>(val module: Module,
                                                                                            val entityReference: EntityReference<E>,
                                                                                            val roots: IndexingRootHolder,
                                                                                            val presentation: IndexableIteratorPresentation?) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forModuleAwareCustomizedContentEntity((module as ModuleBridge).moduleEntityId, entityReference, roots, presentation)
  }

  fun createIterators(): Collection<IndexableFilesIterator> {
    return createModuleAwareContentEntityIterators(module, entityReference, roots, presentation)
  }
}

internal data class EntityGenericContentRootsDescription<E : WorkspaceEntity>(val entityReference: EntityReference<E>,
                                                                              val roots: IndexingRootHolder,
                                                                              val presentation: IndexableIteratorPresentation?) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forGenericContentEntity(entityReference, roots, presentation)
  }

  fun createIterators(): Collection<IndexableFilesIterator> {
    return createGenericContentEntityIterators(entityReference, roots, presentation)
  }
}

internal data class EntityExternalRootsDescription<E : WorkspaceEntity>(val entityReference: EntityReference<E>,
                                                                        val roots: IndexingSourceRootHolder,
                                                                        val presentation: IndexableIteratorPresentation?) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forExternalEntity(entityReference, roots, presentation)
  }

  fun createIterators(): Collection<IndexableFilesIterator> {
    return IndexableEntityProviderMethods.createExternalEntityIterators(entityReference, roots, presentation)
  }
}

internal data class EntityCustomKindRootsDescription<E : WorkspaceEntity>(val entityReference: EntityReference<E>,
                                                                          val roots: IndexingRootHolder,
                                                                          val presentation: IndexableIteratorPresentation?) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forCustomKindEntity(entityReference, roots, presentation)
  }

  fun createIterators(): Collection<IndexableFilesIterator> {
    return createCustomKindEntityIterators(entityReference, roots, presentation)
  }
}

fun selectRootVirtualFiles(value: Collection<VirtualFile>): List<VirtualFile> {
  return selectRootItems(value) { file -> file.path }
}

fun selectRootVirtualFileUrls(urls: Collection<VirtualFileUrl>): List<VirtualFileUrl> {
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
  private val moduleRoots: MutableMap<Module, MutableIndexingRootHolder> = mutableMapOf()
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
    data.customizedModuleContentEntities.putAllValues(newRootData.customizedModuleContentEntities)
    addDiff(data.customizedModuleContentRoots, oldRootData.customizedModuleContentRoots, newRootData.customizedModuleContentRoots)
    addDiff(data.contentRoots, oldRootData.contentRoots, newRootData.contentRoots)
    addSourceDiff(data.libraryRoots, oldRootData.libraryRoots, newRootData.libraryRoots)
    addSourceDiff(data.externalRoots, oldRootData.externalRoots, newRootData.externalRoots)
    data.excludedRoots.addAll(oldRootData.excludedRoots)
    data.excludedRoots.removeAll(newRootData.excludedRoots)
    data.customizationValues.putAll(newRootData.customizationValues)
    loadRegistrarData(data)
  }

  private fun <K> addDiff(base: MutableMap<K, MutableIndexingRootHolder>,
                          oldData: MutableMap<K, MutableIndexingRootHolder>,
                          newData: MutableMap<K, MutableIndexingRootHolder>) {
    newData.entries.forEach { entry ->
      entry.value.remove(oldData[entry.key])
      base.getOrPut(entry.key) { MutableIndexingRootHolder() }.addRoots(entry.value)
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

  private fun <E : WorkspaceEntity> loadRegistrarData(rootData: RootData<E>) {
    rootData.moduleContents.entries.forEach { entry ->
      moduleRoots.getOrPut(entry.key) { MutableIndexingRootHolder() }.addRoots(entry.value)
    }

    for ((module, entityReferences) in rootData.customizedModuleContentEntities.entrySet()) {
      for (entityReference in entityReferences) {
        val roots = rootData.customizedModuleContentRoots[entityReference]
        if (roots?.isEmpty() != false) continue
        descriptions.add(
          EntityContentRootsCustomizedModuleAwareDescription(module, entityReference, roots, rootData.customizationValues[entityReference]))
      }
    }

    for (entry in rootData.contentRoots.entries) {
      descriptions.add(EntityGenericContentRootsDescription(entry.key, entry.value, rootData.customizationValues[entry.key]))
    }

    for ((libraryEntity, roots) in rootData.libraryRoots.entries) {
      descriptions.add(LibraryRootsDescription(libraryEntity, roots))
    }

    for ((entityReference, roots) in rootData.externalRoots.entries) {
      descriptions.add(EntityExternalRootsDescription(entityReference, roots, rootData.customizationValues[entityReference]))
    }

    for ((entityReference, roots) in rootData.customKindRoots.entries) {
      descriptions.add(EntityCustomKindRootsDescription(entityReference, roots, rootData.customizationValues[entityReference]))
    }
    reincludedRoots.addAll(rootData.excludedRoots)
  }

  fun createBuilders(project: Project): Collection<IndexableIteratorBuilder> {
    val builders = mutableListOf<IndexableIteratorBuilder>()
    for (entry in moduleRoots.entries) {
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
    val initialIterators = ArrayList<IndexableFilesIterator>()
    for ((module, roots) in moduleRoots.entries) {
      initialIterators.addAll(IndexableEntityProviderMethods.createIterators(module, roots))
    }
    for (description in descriptions) {
      when (description) {
        is EntityContentRootsCustomizedModuleAwareDescription<*> -> iterators.addAll(description.createIterators())
        is EntityGenericContentRootsDescription<*> -> iterators.addAll(description.createIterators())
        is LibraryRootsDescription -> {
          description.createIterators(storage).forEach { iterator ->
            if (libraryOriginsToFilterDuplicates.add(iterator.origin)) {
              initialIterators.add(iterator)
            }
          }
        }
        is EntityExternalRootsDescription<*> -> iterators.addAll(description.createIterators())
        is EntityCustomKindRootsDescription<*> -> iterators.addAll(description.createIterators())
      }
    }
    iterators.addAll(0, initialIterators)
  }

  fun <E : WorkspaceEntity> registerEntitiesFromContributor(contributor: WorkspaceFileIndexContributor<E>,
                                                            entityStorage: EntityStorage) {
    entityStorage.entities(contributor.entityClass).forEach { entity ->
      registerAddedEntity(entity, contributor, entityStorage)
    }
  }

  fun forEachModuleContentEntitiesRoots(consumer: Consumer<IndexingRootHolder>) {
    for (entry in moduleRoots.entries) {
      consumer.accept(entry.value)
    }
  }

  fun forEachContentEntitiesRoots(consumer: Consumer<IndexingRootHolder>) {
    for (description in descriptions) {
      if (description is EntityGenericContentRootsDescription<*>) {
        consumer.accept(description.roots)
      }
    }
  }

  fun forEachExternalEntitiesRoots(consumer: Consumer<IndexingSourceRootHolder>) {
    for (description in descriptions) {
      if (description is EntityExternalRootsDescription<*>) {
        consumer.accept(description.roots)
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
  val moduleContents = mutableMapOf<Module, MutableIndexingRootHolder>()
  val customizedModuleContentEntities = MultiMap<Module, EntityReference<E>>()
  val customizedModuleContentRoots = mutableMapOf<EntityReference<E>, MutableIndexingRootHolder>()
  val contentRoots = mutableMapOf<EntityReference<E>, MutableIndexingRootHolder>()
  val libraryRoots = mutableMapOf<LibraryEntity, MutableIndexingSourceRootHolder>()
  val externalRoots = mutableMapOf<EntityReference<E>, MutableIndexingSourceRootHolder>()
  val customKindRoots = mutableMapOf<EntityReference<E>, MutableIndexingRootHolder>()
  val excludedRoots = mutableListOf<VirtualFile>()

  val customizationValues = mutableMapOf<EntityReference<*>, IndexableIteratorPresentation>()

  private fun fillCustomizationValues(entity: E, entityReference: EntityReference<E>) {
    if (contributor.entityClass.isAssignableFrom(entity.getEntityInterface())) {
      if (contributor is CustomizingIndexingPresentationContributor<E>) {
        contributor.customizeIteratorPresentation(entity)?.let { customizationValues[entityReference] = it }
      }
    }
    else {
      thisLogger().error("Entity $entity is registered from WorkspaceFileIndexContributor $contributor of other class")
    }
  }

  fun registerFileSet(root: VirtualFile,
                      kind: WorkspaceFileKind,
                      entity: E,
                      customData: WorkspaceFileSetData?,
                      recursive: Boolean) {
    val entityReference = entity.createReference<E>()
    fillCustomizationValues(entity, entityReference)

    fun <K> addRoot(map: MutableMap<K, MutableIndexingRootHolder>, key: K) {
      val holder = map.getOrPut(key) { MutableIndexingRootHolder() }
      (if (recursive) holder.roots else holder.nonRecursiveRoots).add(root)
    }

    fun <K> addRoot(map: MutableMap<K, MutableIndexingSourceRootHolder>, key: K, sourceRoot: Boolean) {
      val holder = map.getOrPut(key) { MutableIndexingSourceRootHolder() }
      if (sourceRoot) {
        (if (recursive) holder.sourceRoots else holder.nonRecursiveSourceRoots).add(root)
      }
      else {
        (if (recursive) holder.roots else holder.nonRecursiveRoots).add(root)
      }
    }

    if (customData is ModuleRelatedRootData) {
      if (!ignoreModuleRoots) {
        if (contributor is CustomizingIndexingPresentationContributor<E>) {
          customizedModuleContentEntities.putValue(customData.module, entityReference)
          addRoot(customizedModuleContentRoots, entityReference)
        }
        else {
          addRoot(moduleContents, customData.module)
        }
      }
    }
    else if (kind.isContent) {
      addRoot(contentRoots, entityReference)
    }
    else if (contributor is LibraryRootFileIndexContributor) {
      addRoot(libraryRoots, entity as LibraryEntity, kind === WorkspaceFileKind.EXTERNAL_SOURCE)
    }
    else if (kind == WorkspaceFileKind.CUSTOM) {
      addRoot(customKindRoots, entityReference)
    }
    else {
      addRoot(externalRoots, entityReference, kind === WorkspaceFileKind.EXTERNAL_SOURCE)
    }
  }

  fun registerFileSet(root: VirtualFileUrl,
                      kind: WorkspaceFileKind,
                      entity: WorkspaceEntity,
                      customData: WorkspaceFileSetData?,
                      recursive: Boolean) {
    root.virtualFile?.let { registerFileSet(it, kind, entity as E, customData, recursive) }
  }

  fun registerExcludedRoot(root: VirtualFile) {
    excludedRoots.add(root)
  }

  fun registerExcludedRoot(root: VirtualFileUrl) {
    root.virtualFile?.let { excludedRoots.add(it) }
  }

  fun cleanIncludedRoots() {
    moduleContents.clear()
    customizedModuleContentRoots.clear()
    customizedModuleContentEntities.clear()
    contentRoots.clear()
    libraryRoots.clear()
    externalRoots.clear()
    customKindRoots.clear()
    customizationValues.clear()
  }

  fun cleanExcludedRoots() {
    excludedRoots.clear()
  }
}

private class MyWorkspaceFileSetRegistrar<E : WorkspaceEntity>(contributor: WorkspaceFileIndexContributor<E>,
                                                               ignoreModuleRoots: Boolean) : WorkspaceFileSetRegistrar {
  val rootData: RootData<E> = RootData(contributor, ignoreModuleRoots)

  override fun registerFileSet(root: VirtualFileUrl, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    rootData.registerFileSet(root, kind, entity, customData, true)
  }

  override fun registerFileSet(root: VirtualFile, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    rootData.registerFileSet(root, kind, entity as E, customData, true)
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

  override fun registerNonRecursiveFileSet(file: VirtualFileUrl,
                                           kind: WorkspaceFileKind,
                                           entity: WorkspaceEntity,
                                           customData: WorkspaceFileSetData?) {
    rootData.registerFileSet(file, kind, entity, customData, false)
  }
}


