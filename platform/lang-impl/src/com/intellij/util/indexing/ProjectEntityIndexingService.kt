// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.SmartList
import com.intellij.util.indexing.BuildableRootsChangeRescanningInfoImpl.BuiltRescanningInfo
import com.intellij.util.indexing.EntityIndexingServiceImpl.WorkspaceEntitiesRootsChangedRescanningInfo
import com.intellij.util.indexing.EntityIndexingServiceImpl.WorkspaceEventRescanningInfo
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService.StatusMark
import com.intellij.util.indexing.roots.GenericDependencyIterator
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableEntityProvider.*
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.WorkspaceIndexingRootsBuilder
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.origin.LibraryOriginImpl
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription.OnParent
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl.Companion.EP_NAME
import com.intellij.workspaceModel.core.fileIndex.impl.getEntityPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable


@ApiStatus.Internal
@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class ProjectEntityIndexingService(
  private val project: Project,
  private val scope: CoroutineScope,
) : WorkspaceFileIndexListener {

  private val tracker = CustomEntitiesCausingReindexTracker()

  fun indexChanges(changes: List<RootsChangeRescanningInfo>) {
    if (FileBasedIndex.getInstance() !is FileBasedIndexImpl) return
    if (LightEdit.owns(project)) return
    if (invalidateProjectFilterIfFirstScanningNotRequested(project)) return

    if (ModalityState.defaultModalityState() === ModalityState.any()) {
      LOG.error("Unexpected modality: should not be ANY. Replace with NON_MODAL (130820241337)")
    }

    if (changes.isEmpty()) {
      logRootChanges(project, true)
      UnindexedFilesScanner(project, "Project roots have changed").queue()
    }
    else {
      val parameters = computeScanningParameters(changes)
      UnindexedFilesScanner(project, parameters).queue()
    }
  }

  override fun workspaceFileIndexChanged(event: WorkspaceFileIndexChangedEvent) {
    if (!Registry.`is`("use.workspace.file.index.for.partial.scanning")) return
    if (FileBasedIndex.getInstance() !is FileBasedIndexImpl) return
    if (LightEdit.owns(project)) return
    if (invalidateProjectFilterIfFirstScanningNotRequested(project)) return

    if (ModalityState.defaultModalityState() === ModalityState.any()) {
      LOG.error("Unexpected modality: should not be ANY. Replace with NON_MODAL (130820241337)")
    }

    if (event.registeredFileSets.isNotEmpty() || event.removedFileSets.isNotEmpty()) {
      val parameters =  computeScanningParametersFromWFIEvent(event)
      UnindexedFilesScanner(project, parameters).queue()
    }
  }

  private fun computeScanningParametersFromWFIEvent(event: WorkspaceFileIndexChangedEvent): Deferred<ScanningParameters> {
    return scope.async {
      ReadAction.nonBlocking(Callable {
        val iterators = ArrayList<IndexableFilesIterator>()

        //generateIteratorsFromWFIChangedEvent(event.removedFileSets, event.storageBefore, iterators)
        generateIteratorsFromWFIChangedEvent(event.registeredFileSets, event.storageAfter, iterators)

        return@Callable if (iterators.isEmpty()) {
          CancelledScanning
        }
        else {
          ScanningIterators("Changes from WorkspaceFileIndex", predefinedIndexableFilesIterators = iterators)
        }
      }).executeSynchronously()
    }
  }

  private enum class Change {
    Added, Replaced, Removed;

    companion object {
      fun fromEntityChange(change: EntityChange<*>?): Change {
        if (change is EntityChange.Added<*>) return Added
        if (change is EntityChange.Replaced<*>) return Replaced
        if (change is EntityChange.Removed<*>) return Removed
        throw IllegalStateException("Unexpected change $change")
      }
    }
  }

  fun shouldCauseRescan(oldEntity: WorkspaceEntity?, newEntity: WorkspaceEntity?): Boolean {
    return tracker.shouldRescan(oldEntity, newEntity, project)
  }

  private fun generateIteratorsFromWFIChangedEvent(
    fileSets: Collection<WorkspaceFileSet>,
    storage: EntityStorage,
    iterators: MutableList<IndexableFilesIterator>,
  ) {
    val libraryOrigins = HashSet<LibraryOrigin>()

    for (fileSet in fileSets) {
      fileSet as WorkspaceFileSetWithCustomData<*>
      val entityPointer = fileSet.getEntityPointer() ?: continue
      if (!fileSet.kind.isIndexable) continue
      if (fileSet.data is ModuleRelatedRootData) continue
      if (fileSet.kind.isContent) continue

      val entity = entityPointer.resolve(storage) ?: continue
      if (entity is LibraryEntity) {
        val sourceRoot = fileSet.kind == WorkspaceFileKind.EXTERNAL_SOURCE
        val origin = if (sourceRoot) {
          LibraryOriginImpl(emptyList(), listOf(fileSet.root))
        }
        else {
          LibraryOriginImpl(listOf(fileSet.root), emptyList())
        }
        val iterator = GenericDependencyIterator.forLibraryEntity(origin, entity.name, fileSet.root, sourceRoot)
        if (libraryOrigins.add(origin)) {
          iterators.add(iterator)
        }
      }
    }
  }

  private fun computeScanningParameters(changes: List<RootsChangeRescanningInfo>): Deferred<ScanningParameters> {
    return scope.async {
      var indexDependencies = false
      for (change in changes) {
        if (change === RootsChangeRescanningInfo.TOTAL_RESCAN) {
          return@async ScanningIterators(
            "Reindex requested by project root model changes",
          )
        }
        else if (change === RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED) {
          if (!indexDependencies && !DependenciesIndexedStatusService.shouldBeUsed()) {
            return@async ScanningIterators(
              "Reindex of changed dependencies requested, but not enabled",
            )
          }
          else {
            indexDependencies = true
          }
        }
      }
      val builders = SmartList<IndexableIteratorBuilder>()

      var dependenciesStatusMark: StatusMark? = null
      if (indexDependencies) {
        val dependencyBuildersPair = DependenciesIndexedStatusService.getInstance(project).getDeltaWithLastIndexedStatus()
        if (dependencyBuildersPair == null) {
          return@async ScanningIterators(
            "Reindex of changed dependencies requested, but status is not initialized",
          )
        }
        builders.addAll(dependencyBuildersPair.first)
        dependenciesStatusMark = dependencyBuildersPair.second
      }

      val entityStorage = project.serviceAsync<WorkspaceModel>().currentSnapshot
      for (change in changes) {
        if (change === RootsChangeRescanningInfo.NO_RESCAN_NEEDED || change === RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED) {
          continue
        }
        if (change is WorkspaceEventRescanningInfo) {
          builders.addAll(getBuildersOnWorkspaceChange(project, change.events, entityStorage))
        }
        else if (change is WorkspaceEntitiesRootsChangedRescanningInfo) {
          val pointers = change.pointers
          val entities = pointers.mapNotNull { ref ->
            ref.resolve(entityStorage)
          }
          builders.addAll(getBuildersOnWorkspaceEntitiesRootsChange(project, entities, entityStorage))
        }
        else if (change is BuiltRescanningInfo) {
          builders.addAll(getBuildersOnBuildableChangeInfo(change))
        }
        else {
          LOG.warn("Unexpected change " + change.javaClass + " " + change + ", full reindex requested")
          return@async ScanningIterators(
            "Reindex on unexpected change in EntityIndexingServiceImpl",
          )
        }
      }
      if (!builders.isEmpty()) {
        val mergedIterators = IndexableIteratorBuilders.instantiateBuilders(builders, project, entityStorage)

        if (!mergedIterators.isEmpty()) {
          val debugNames = mergedIterators.map { obj -> obj.getDebugName() }
          LOG.debug("Accumulated iterators: $debugNames")
          val maxNamesToLog = 10
          var reasonMessage = "changes in: " + debugNames
            .asSequence()
            .take(maxNamesToLog)
            .map { str -> StringUtil.wrapWithDoubleQuote(str) }
            .joinToString(", ")
          if (debugNames.size > maxNamesToLog) {
            reasonMessage += " and " + (debugNames.size - maxNamesToLog) + " iterators more"
          }
          logRootChanges(project, false)
          return@async ScanningIterators(
            reasonMessage,
            mergedIterators,
            dependenciesStatusMark
          )
        }
      }
      return@async CancelledScanning
    }
  }

  companion object {
    private val LOG = Logger.getInstance(ProjectEntityIndexingService::class.java)
    private val ROOT_CHANGES_LOGGER = RootChangesLogger()

    fun getInstance(project: Project): ProjectEntityIndexingService {
      return project.service()
    }

    private fun logRootChanges(project: Project, isFullReindex: Boolean) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        if (LOG.isDebugEnabled()) {
          val message = if (isFullReindex) "Project roots of " + project.getName() + " have changed" else "Project roots of " + project.getName() + " will be partially reindexed"
          LOG.debug(message, Throwable())
        }
      }
      else {
        ROOT_CHANGES_LOGGER.info(project, isFullReindex)
      }
    }

    @TestOnly
    fun getIterators(
      project: Project,
      events: Collection<EntityChange<*>>,
    ): List<IndexableFilesIterator> {
      val entityStorage: EntityStorage = WorkspaceModel.getInstance(project).currentSnapshot
      val result = getBuildersOnWorkspaceChange(project, events, entityStorage)
      return IndexableIteratorBuilders.instantiateBuilders(result, project, entityStorage)
    }

    private fun getBuildersOnWorkspaceChange(
      project: Project,
      events: Collection<EntityChange<*>>,
      entityStorage: EntityStorage,
    ): List<IndexableIteratorBuilder> {
      val builders = SmartList<IndexableIteratorBuilder>()
      val descriptionsBuilder = WorkspaceIndexingRootsBuilder(false)
      for (change in events) {
        collectIteratorBuildersOnChange(Change.fromEntityChange(change), change.oldEntity,
                                        change.newEntity, project, builders,
                                        descriptionsBuilder, entityStorage)
      }
      builders.addAll(descriptionsBuilder.createBuilders(project))
      return builders
    }

    private fun <E : WorkspaceEntity> collectIteratorBuildersOnChange(
      change: Change,
      oldEntity: E?,
      newEntity: E?,
      project: Project,
      builders: MutableCollection<in IndexableIteratorBuilder>,
      descriptionsBuilder: WorkspaceIndexingRootsBuilder,
      entityStorage: EntityStorage,
    ) {
      LOG.assertTrue(newEntity != null || change == Change.Removed, "New entity $newEntity, change $change")
      LOG.assertTrue(oldEntity != null || change == Change.Added, "Old entity $oldEntity, change $change")

      val entityClass = (newEntity ?: oldEntity)!!.getEntityInterface() as Class<in E>

      val newBuilders = ArrayList<IndexableIteratorBuilder>()
      collectWFICIteratorsOnChange(change, oldEntity, newEntity, project, newBuilders, descriptionsBuilder, entityClass,
                                   entityStorage)

      builders.addAll(newBuilders)
    }


    private fun <E : WorkspaceEntity> collectIEPIteratorsOnChange(
      change: Change,
      oldEntity: E?,
      newEntity: E?,
      project: Project,
      builders: MutableCollection<in IndexableIteratorBuilder>,
      entityClass: Class<in E>,
    ) {
      LOG.assertTrue(newEntity != null || change == Change.Removed, "New entity $newEntity, change $change")
      LOG.assertTrue(oldEntity != null || change == Change.Added, "Old entity $oldEntity, change $change")

      for (uncheckedProvider in IndexableEntityProvider.EP_NAME.extensionList) {
        if (entityClass == uncheckedProvider.getEntityClass() && uncheckedProvider is Enforced<*>) {
          @Suppress("UNCHECKED_CAST")
          uncheckedProvider as (IndexableEntityProvider<E>)
          val generated = when (change) {
            Change.Added -> uncheckedProvider.getAddedEntityIteratorBuilders(newEntity!!, project)
            Change.Replaced -> uncheckedProvider.getReplacedEntityIteratorBuilders(oldEntity!!, newEntity!!, project)
            Change.Removed -> uncheckedProvider.getRemovedEntityIteratorBuilders(oldEntity!!, project)
          }
          builders.addAll(generated)
        }

        if (change == Change.Replaced && uncheckedProvider is Enforced<*>) {
          for (dependency in uncheckedProvider.dependencies) {
            if (entityClass == dependency.getParentClass()) {
              @Suppress("UNCHECKED_CAST")
              dependency as DependencyOnParent<E>
              builders.addAll(dependency.getReplacedEntityIteratorBuilders(oldEntity!!, newEntity!!))
            }
          }
        }
      }
    }

    private fun <E : WorkspaceEntity> collectWFICIteratorsOnChange(
      change: Change,
      oldEntity: E?,
      newEntity: E?,
      project: Project,
      builders: MutableCollection<in IndexableIteratorBuilder>,
      descriptionsBuilder: WorkspaceIndexingRootsBuilder,
      entityClass: Class<in E>,
      entityStorage: EntityStorage,
    ) {
      LOG.assertTrue(newEntity != null || change == Change.Removed, "New entity $newEntity, change $change")
      LOG.assertTrue(oldEntity != null || change == Change.Added, "Old entity $oldEntity, change $change")

      val contributors = EP_NAME.extensionList
      for (uncheckedContributor in contributors) {
        if (uncheckedContributor.storageKind != EntityStorageKind.MAIN) {
          continue
        }
        if (entityClass == uncheckedContributor.entityClass) {
          @Suppress("UNCHECKED_CAST")
          uncheckedContributor as WorkspaceFileIndexContributor<E>
          when (change) {
            Change.Added -> descriptionsBuilder.registerAddedEntity(newEntity!!, uncheckedContributor, entityStorage)
            Change.Replaced -> descriptionsBuilder.registerChangedEntity(oldEntity!!, newEntity!!, uncheckedContributor, entityStorage)
            Change.Removed -> descriptionsBuilder.registerRemovedEntity(oldEntity!!, uncheckedContributor, entityStorage)
          }
        }
        handleRelativeEntities(entityClass, oldEntity, newEntity, descriptionsBuilder, uncheckedContributor, entityStorage)
        if (change == Change.Replaced) {
          handleDependencies(oldEntity!!, newEntity!!, descriptionsBuilder, entityClass, uncheckedContributor,
                             entityStorage)
        }
      }

      collectIEPIteratorsOnChange(change, oldEntity, newEntity, project, builders, entityClass)
    }

    private fun <E : WorkspaceEntity, C : WorkspaceEntity> handleDependencies(
      oldEntity: E,
      newEntity: E,
      descriptionsBuilder: WorkspaceIndexingRootsBuilder,
      entityClass: Class<in E>,
      contributor: WorkspaceFileIndexContributor<C>,
      entityStorage: EntityStorage,
    ) {
      for (dependency in contributor.dependenciesOnOtherEntities) {
        handleChildEntities(entityClass, oldEntity, newEntity, descriptionsBuilder, contributor, dependency, entityStorage)
      }
    }

    private fun <E : WorkspaceEntity, C : WorkspaceEntity> handleRelativeEntities(
      entityClass: Class<in E>,
      oldEntity: E?,
      newEntity: E?,
      descriptionsBuilder: WorkspaceIndexingRootsBuilder,
      contributor: WorkspaceFileIndexContributor<C>,
      entityStorage: EntityStorage,
    ) {
      for (dependency in contributor.dependenciesOnOtherEntities) {
        if (dependency !is DependencyDescription.OnArbitraryEntity<*, *> || entityClass != dependency.entityClass) {
          continue
        }
        @Suppress("UNCHECKED_CAST")
        dependency as DependencyDescription.OnArbitraryEntity<C, E>

        val removedEntities: MutableSet<C> = mutableSetOf()
        val addedEntities: MutableSet<C> = mutableSetOf()
        oldEntity?.let {
          dependency.dependantEntitiesGetter(it).toCollection(removedEntities)
        }
        newEntity?.let {
          dependency.dependantEntitiesGetter(it).toCollection(addedEntities)
        }
        val entitiesToKeep = mutableSetOf<C>()
        val entitiesToRemove = mutableSetOf<C>()
        val entitiesInCurrentStorage = entityStorage.entities(contributor.entityClass).toSet()

        if (removedEntities.isNotEmpty()) {
          entitiesToKeep.addAll(entitiesInCurrentStorage.intersect(removedEntities))
        }
        if (addedEntities.isNotEmpty()) {
          entitiesToRemove.addAll(addedEntities - entitiesInCurrentStorage)
        }

        for (element in addedEntities) {
          descriptionsBuilder.registerAddedEntity(element, contributor, entityStorage)
        }
        for (element in removedEntities) {
          descriptionsBuilder.registerRemovedEntity(element, contributor, entityStorage)
        }
        for (element in entitiesToKeep) {
          descriptionsBuilder.registerAddedEntity(element, contributor, entityStorage)
        }
        for (element in entitiesToRemove) {
          descriptionsBuilder.registerRemovedEntity(element, contributor, entityStorage)
        }
      }
    }

    private fun <E : WorkspaceEntity, C : WorkspaceEntity> handleChildEntities(
      entityClass: Class<in E>,
      oldEntity: E,
      newEntity: E,
      descriptionsBuilder: WorkspaceIndexingRootsBuilder,
      contributor: WorkspaceFileIndexContributor<C>,
      dependency: DependencyDescription<C>,
      entityStorage: EntityStorage,
    ) {
      if (dependency !is OnParent<*, *> || entityClass != dependency.parentClass) {
        return
      }
      @Suppress("UNCHECKED_CAST")
      val oldElements = (dependency as OnParent<C, E>).childrenGetter(oldEntity).toList<C>()
      val newElements = dependency.childrenGetter(newEntity).toMutableList<C>()

      newElements.removeAll(oldElements)
      for (element in newElements) {
        descriptionsBuilder.registerAddedEntity(element, contributor, entityStorage)
      }
    }

    private fun getBuildersOnWorkspaceEntitiesRootsChange(
      project: Project,
      entities: Collection<WorkspaceEntity>,
      entityStorage: EntityStorage,
    ): MutableCollection<out IndexableIteratorBuilder> {
      if (entities.isEmpty()) return mutableListOf<IndexableIteratorBuilder>()
      val builders = SmartList<IndexableIteratorBuilder>()

      val descriptionsBuilder = WorkspaceIndexingRootsBuilder(false)
      for (entity in entities) {
        collectIteratorBuildersOnChange(Change.Added, null, entity, project, builders, descriptionsBuilder, entityStorage)
      }
      builders.addAll(descriptionsBuilder.createBuilders(project))
      return builders
    }

    private fun getBuildersOnBuildableChangeInfo(
      info: BuiltRescanningInfo,
    ): MutableCollection<out IndexableIteratorBuilder> {
      val builders = SmartList<IndexableIteratorBuilder>()
      val instance = IndexableIteratorBuilders
      if (info.hasInheritedSdk) {
        builders.addAll(instance.forInheritedSdk())
      }
      for (sdk in info.sdks) {
        builders.add(instance.forSdk(sdk.first, sdk.second))
      }
      for (library in info.libraries) {
        builders.addAll(instance.forLibraryEntity(library, true))
      }
      return builders
    }
  }
}
