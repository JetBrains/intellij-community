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
import com.intellij.util.containers.MultiMap
import com.intellij.util.indexing.CustomizingIndexingContributor
import com.intellij.util.indexing.ReincludedRootsUtil
import com.intellij.util.indexing.customizingIteration.GenericContentEntityIterator
import com.intellij.util.indexing.customizingIteration.ModuleAwareContentEntityIterator
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createGenericContentEntityIterators
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl.Companion.createIterator
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forExternalEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forGenericContentEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forLibraryEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forModuleAwareCustomizedContentEntity
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders.forModuleRootsFileBased
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.core.fileIndex.impl.LibraryRootFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.virtualFile
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.jps.util.JpsPathUtil
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

internal sealed interface IndexingRootsDescription {
  fun createBuilders(): Collection<IndexableIteratorBuilder>
}

internal data class LibraryRootsDescription(val library: LibraryEntity,
                                            val classRoots: List<VirtualFile>,
                                            val sourceRoots: List<VirtualFile>) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forLibraryEntity(library.symbolicId, false, classRoots, sourceRoots)
  }

  fun createIterators(storage: EntityStorage): Collection<IndexableFilesIterator> {
    val library = library.findLibraryBridge(storage) ?: return emptyList()
    return listOfNotNull(createIterator(library, classRoots, sourceRoots))
  }
}

internal data class IndexingContributorCustomization<E : WorkspaceEntity, D>(val customizingContributor: CustomizingIndexingContributor<E, D>,
                                                                             val value: D?) {
  companion object {
    fun <E : WorkspaceEntity, D> create(customizingContributor: CustomizingIndexingContributor<E, D>,
                                     entity: E): IndexingContributorCustomization<E, D> {
      return IndexingContributorCustomization(customizingContributor, customizingContributor.getCustomizationData(entity))
    }
  }

  fun createModuleAwareContentIterators(module: Module,
                                        entityReference: EntityReference<E>,
                                        roots: Collection<VirtualFile>): Collection<ModuleAwareContentEntityIterator> {
    return customizingContributor.createModuleAwareContentIterators(module, entityReference, roots, value)
  }

  fun createGenericContentIterators(entityReference: EntityReference<E>,
                                    roots: Collection<VirtualFile>): Collection<GenericContentEntityIterator> {
    return customizingContributor.createGenericContentIterators(entityReference, roots, value)
  }

  fun createExternalEntityIterators(entityReference: EntityReference<E>,
                                    roots: Collection<VirtualFile>,
                                    sourceRoots: Collection<VirtualFile>): Collection<IndexableFilesIterator> {
    return customizingContributor.createExternalEntityIterators(entityReference, roots, sourceRoots, value)
  }
}

internal data class EntityContentRootsCustomizedModuleAwareDescription<E : WorkspaceEntity>(val module: Module,
                                                                                            val entityReference: EntityReference<E>,
                                                                                            val roots: Collection<VirtualFile>,
                                                                                            val customization: IndexingContributorCustomization<E, *>) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forModuleAwareCustomizedContentEntity((module as ModuleBridge).moduleEntityId, entityReference, roots, customization)
  }

  fun createIterators(): Collection<IndexableFilesIterator> {
    return customization.createModuleAwareContentIterators(module, entityReference, roots)
  }
}

internal data class EntityGenericContentRootsDescription<E : WorkspaceEntity>(val entityReference: EntityReference<E>,
                                                                              val roots: Collection<VirtualFile>,
                                                                              val customization: IndexingContributorCustomization<E, *>?) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forGenericContentEntity(entityReference, roots, customization)
  }

  fun createIterators(): Collection<IndexableFilesIterator> {
    return customization?.createGenericContentIterators(entityReference, roots) ?: createGenericContentEntityIterators(
      entityReference, roots)
  }
}

internal data class EntityExternalRootsDescription<E : WorkspaceEntity>(val entityReference: EntityReference<E>,
                                                                        val roots: Collection<VirtualFile>,
                                                                        val sourceRoots: Collection<VirtualFile>,
                                                                        val customization: IndexingContributorCustomization<E, *>?) : IndexingRootsDescription {
  override fun createBuilders(): Collection<IndexableIteratorBuilder> {
    return forExternalEntity(entityReference, roots, sourceRoots, customization)
  }

  fun createIterators(): Collection<IndexableFilesIterator> {
    return customization?.createExternalEntityIterators(entityReference, roots, sourceRoots)
           ?: IndexableEntityProviderMethods.createExternalEntityIterators(entityReference, roots, sourceRoots)
  }
}

private fun <T> toList(value: Collection<T>): List<T> {
  if (value is List<T>) return value
  return if (value.isEmpty()) emptyList() else ArrayList(value)
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
                                             storage: EntityStorage): RootData<E> {
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

    val data = RootData(contributor)
    data.moduleContents.putAllValues(diff(oldRootData.moduleContents, newRootData.moduleContents))
    data.customizedModuleContentEntities.putAllValues(newRootData.customizedModuleContentEntities)
    data.customizedModuleContentRoots.putAllValues(diff(oldRootData.customizedModuleContentRoots, newRootData.customizedModuleContentRoots))
    data.contentRoots.putAllValues(diff(oldRootData.contentRoots, newRootData.contentRoots))
    data.libraryRoots.putAllValues(diff(oldRootData.libraryRoots, newRootData.libraryRoots))
    data.librarySourceRoots.putAllValues(diff(oldRootData.librarySourceRoots, newRootData.librarySourceRoots))
    data.externalRoots.putAllValues(diff(oldRootData.externalRoots, newRootData.externalRoots))
    data.externalSourceRoots.putAllValues(diff(oldRootData.externalSourceRoots, newRootData.externalSourceRoots))
    data.excludedRoots.addAll(oldRootData.excludedRoots)
    data.excludedRoots.removeAll(newRootData.excludedRoots)
    data.customizationValues.putAll(newRootData.customizationValues)
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

  private fun <E : WorkspaceEntity> loadRegistrarData(rootData: RootData<E>) {
    moduleRoots.putAllValues(rootData.moduleContents)

    val customization: Function<EntityReference<E>, IndexingContributorCustomization<E, *>?> = if (rootData.contributor is CustomizingIndexingContributor<E, *>) {
      Function { reference ->
        IndexingContributorCustomization(rootData.contributor as CustomizingIndexingContributor<E, Any>,
                                         rootData.customizationValues[reference])
      }
    }
    else {
      Function { null }
    }

    for ((module, entityReferences) in rootData.customizedModuleContentEntities.entrySet()) {
      for (entityReference in entityReferences) {
        val roots = rootData.customizedModuleContentRoots[entityReference]
        if (roots.isEmpty()) continue
        descriptions.add(
          EntityContentRootsCustomizedModuleAwareDescription(module, entityReference, roots, customization.apply(entityReference)!!))
      }
    }

    for (entry in rootData.contentRoots.entrySet()) {
      descriptions.add(EntityGenericContentRootsDescription(entry.key, entry.value, customization.apply(entry.key)))
    }

    for ((libraryEntity, roots) in rootData.libraryRoots.entrySet()) {
      val sourceRoots = (rootData.librarySourceRoots.remove(libraryEntity)) ?: emptyList()
      descriptions.add(LibraryRootsDescription(libraryEntity, toList(roots), toList(sourceRoots)))
    }
    for ((libraryEntity, sourceRoots) in rootData.librarySourceRoots.entrySet()) {
      descriptions.add(LibraryRootsDescription(libraryEntity, emptyList(), toList(sourceRoots)))
    }

    for ((entityReference, roots) in rootData.externalRoots.entrySet()) {
      var sourceRoots = rootData.externalSourceRoots.remove(entityReference)
      sourceRoots = sourceRoots ?: emptyList()
      descriptions.add(EntityExternalRootsDescription(entityReference, roots, sourceRoots, customization.apply(entityReference)))
    }
    for ((entityReference, roots) in rootData.externalSourceRoots.entrySet()) {
      descriptions.add(EntityExternalRootsDescription(entityReference, emptyList(), roots, customization.apply(entityReference)))
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
    val initialIterators = ArrayList<IndexableFilesIterator>()
    for ((module, roots) in moduleRoots.entrySet()) {
      initialIterators.add(ModuleIndexableFilesIteratorImpl(module, selectRootVirtualFiles(roots), true))
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

  fun forEachModuleContentEntitiesRoots(consumer: Consumer<Collection<VirtualFile>>) {
    for (entry in moduleRoots.entrySet()) {
      consumer.accept(entry.value)
    }
  }

  fun forEachContentEntitiesRoots(consumer: Consumer<Collection<VirtualFile>>) {
    for (description in descriptions) {
      if (description is EntityGenericContentRootsDescription<*>) {
        consumer.accept(description.roots)
      }
    }
  }

  fun forEachExternalEntitiesRoots(rootsConsumer: Consumer<Collection<VirtualFile>>,
                                   sourceRootsConsumer: Consumer<Collection<VirtualFile>>) {
    for (description in descriptions) {
      if (description is EntityExternalRootsDescription<*>) {
        rootsConsumer.accept(description.roots)
        sourceRootsConsumer.accept(description.sourceRoots)
      }
    }
  }

  companion object {
    @JvmOverloads
    fun registerEntitiesFromContributors(project: Project,
                                         entityStorage: EntityStorage,
                                         settings: Settings = Settings.DEFAULT): WorkspaceIndexingRootsBuilder {
      val builder = WorkspaceIndexingRootsBuilder()
      val contributors = (WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexImpl).contributors
      for (contributor in contributors) {
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
      fun shouldIgnore(contributor: WorkspaceFileIndexContributor<*>): Boolean {
        val condition = retainCondition
        return condition != null && !condition.value(contributor)
      }
    }
  }
}

private class RootData<E : WorkspaceEntity>(val contributor: WorkspaceFileIndexContributor<E>) {
  val moduleContents = MultiMap.create<Module, VirtualFile>()
  val customizedModuleContentEntities = MultiMap<Module, EntityReference<E>>()
  val customizedModuleContentRoots = MultiMap.createSet<EntityReference<E>, VirtualFile>()
  val contentRoots = MultiMap.createSet<EntityReference<E>, VirtualFile>()
  val libraryRoots = MultiMap.createSet<LibraryEntity, VirtualFile>()
  val librarySourceRoots = MultiMap.createSet<LibraryEntity, VirtualFile>()
  val externalRoots = MultiMap.createSet<EntityReference<E>, VirtualFile>()
  val externalSourceRoots = MultiMap.createSet<EntityReference<E>, VirtualFile>()
  val excludedRoots = mutableListOf<VirtualFile>()

  val customizationValues = mutableMapOf<EntityReference<*>, Any>()

  private fun fillCustomizationValues(entity: WorkspaceEntity, entityReference: EntityReference<E>) {
    if (contributor.entityClass.isAssignableFrom(entity.getEntityInterface())) {
      if (contributor is CustomizingIndexingContributor<E, *>) {
        contributor.getCustomizationData(entity as E)?.let { customizationValues[entityReference] = it }
      }
    }
    else {
      thisLogger().error("Entity $entity is registered from WorkspaceFileIndexContributor $contributor of other class")
    }
  }

  fun registerFileSet(root: VirtualFile, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    val entityReference = entity.createReference<E>()
    fillCustomizationValues(entity, entityReference)

    if (customData is ModuleRelatedRootData) {
      if (contributor is CustomizingIndexingContributor<E, *>) {
        customizedModuleContentEntities.putValue(customData.module, entityReference)
        customizedModuleContentRoots.putValue(entityReference, root)
      }
      else {
        moduleContents.putValue(customData.module, root)
      }
    }
    else if (kind.isContent) {
      contentRoots.putValue(entityReference, root)
    }
    else if (kind === WorkspaceFileKind.EXTERNAL) {
      if (contributor is LibraryRootFileIndexContributor) {
        libraryRoots.putValue(entity as LibraryEntity, root)
      }
      else {
        externalRoots.putValue(entityReference, root)
      }
    }
    else {
      if (contributor is LibraryRootFileIndexContributor) {
        librarySourceRoots.putValue(entity as LibraryEntity, root)
      }
      else {
        externalSourceRoots.putValue(entityReference, root)
      }
    }
  }

  fun registerFileSet(root: VirtualFileUrl, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    root.virtualFile?.let { registerFileSet(it, kind, entity, customData) }
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
    librarySourceRoots.clear()
    externalRoots.clear()
    externalSourceRoots.clear()
    customizationValues.clear()
  }

  fun cleanExcludedRoots() {
    excludedRoots.clear()
  }
}

private class MyWorkspaceFileSetRegistrar<E : WorkspaceEntity>(contributor: WorkspaceFileIndexContributor<E>) : WorkspaceFileSetRegistrar {
  //todo[lene] inline
  val rootData: RootData<E> = RootData(contributor)

  override fun registerFileSet(root: VirtualFileUrl, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    rootData.registerFileSet(root, kind, entity, customData)
  }

  override fun registerFileSet(root: VirtualFile, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    rootData.registerFileSet(root, kind, entity, customData)
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
    rootData.registerFileSet(file, kind, entity, customData)
  }
}


