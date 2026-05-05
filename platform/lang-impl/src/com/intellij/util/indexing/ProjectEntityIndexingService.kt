// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ex.isIndexingActivitiesSuppressedSync
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.SmartList
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService.StatusMark
import com.intellij.util.indexing.roots.GenericDependencyIterator
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.util.indexing.roots.origin.IndexingSourceRootHolder
import com.intellij.util.indexing.roots.processLibraryEntity
import com.intellij.util.indexing.roots.processModuleRoot
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexChangedEvent
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexListener
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.getEntityPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus


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
    if (isIndexingActivitiesSuppressedSync(project)) return
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
    if (FileBasedIndex.getInstance() !is FileBasedIndexImpl) return
    if (isIndexingActivitiesSuppressedSync(project)) return

    if (ModalityState.defaultModalityState() === ModalityState.any()) {
      LOG.error("Unexpected modality: should not be ANY. Replace with NON_MODAL (130820241337)")
    }

    val registeredIndexableFileSets = event.registeredFileSets.filter { it.kind.isIndexable }
    val runScanning = event.removedExclusions.isNotEmpty() || registeredIndexableFileSets.isNotEmpty()

    if (runScanning) {
      if (invalidateProjectFilterIfFirstScanningNotRequested(project)) return

      val event = WorkspaceFileIndexChangedEvent(
        registeredFileSets = registeredIndexableFileSets,
        storageAfter = event.storageAfter,
        removedExclusions = event.removedExclusions,
      )
      val parameters = computeScanningParametersFromWFIEvent(event)
      UnindexedFilesScanner(project, parameters).queue()
    }
  }

  private fun computeScanningParametersFromWFIEvent(event: WorkspaceFileIndexChangedEvent): Deferred<ScanningParameters> {
    return scope.async {
      readAction {
        processWfiEvent(event)
      }
    }
  }

  private fun processWfiEvent(event: WorkspaceFileIndexChangedEvent): ScanningParameters {
    val iterators = ArrayList<IndexableFilesIterator>()
    val wfi = WorkspaceFileIndex.getInstance(project)

    val removedExclusions = event.removedExclusions.mapNotNull { wfi.findFileSet(it, true, true, false, true, true, false, true); }
    generateIteratorsFromWFIChangedEvent(event.registeredFileSets, event.storageAfter, iterators)
    generateIteratorsFromWFIChangedEvent(removedExclusions, event.storageAfter, iterators)

    return if (iterators.isEmpty()) {
      CancelledScanning
    }
    else {
      ScanningIterators("Changes from WorkspaceFileIndex (${iterators.size} iterators)", predefinedIndexableFilesIterators = iterators)
    }
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

      val customData = fileSet.data
      val root = fileSet.root

      if (customData is ModuleRelatedRootData) {
        processModuleRoot(fileSet, project, true)?.let(iterators::add)
      }
      else if (fileSet.kind.isContent) {
        iterators.add(GenericDependencyIterator.forContentRoot(entityPointer, fileSet.recursive, root))
      }
      else {
        // here we always use WFI
        val entity = entityPointer.resolve(storage) ?: continue
        if (entity is LibraryEntity) {
          val (origin, iterator) = processLibraryEntity(entity, fileSet)
          if (libraryOrigins.add(origin)) {
            iterators.add(iterator)
          }
        }
        else if (entity is SdkEntity) {
          iterators.add(GenericDependencyIterator.forSdkEntity(
            sdkName = entity.name,
            sdkType = SdkType.findByName(entity.type),
            sdkHome = entity.homePath?.url,
            root = fileSet.root
          ))
        }
        else if (fileSet.kind == WorkspaceFileKind.CUSTOM) {
          iterators.add(GenericDependencyIterator.forCustomKindRoot(entityPointer, fileSet.recursive, root))
        }
        else {
          val rootHolder = if (fileSet.kind == WorkspaceFileKind.EXTERNAL_SOURCE) {
            IndexingSourceRootHolder.fromFiles(emptyList(), listOf(root))
          }
          else {
            IndexingSourceRootHolder.fromFiles(listOf(root), emptyList())
          }
          iterators.add(IndexableEntityProviderMethods.createExternalEntityIterators(entityPointer, rootHolder))
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
      val iterators = SmartList<IndexableFilesIterator>()

      var dependenciesStatusMark: StatusMark? = null
      if (indexDependencies) {
        val dependencyIteratorsPair = DependenciesIndexedStatusService.getInstance(project).getDeltaWithLastIndexedStatus()
        if (dependencyIteratorsPair == null) {
          return@async ScanningIterators(
            "Reindex of changed dependencies requested, but status is not initialized",
          )
        }
        iterators.addAll(dependencyIteratorsPair.first)
        dependenciesStatusMark = dependencyIteratorsPair.second
      }

      if (iterators.isNotEmpty()) {
        val debugNames = iterators.map { obj -> obj.getDebugName() }
        LOG.debug("Accumulated iterators: $debugNames")
        val maxNamesToLog = 10
        var reasonMessage = "changes in: " + debugNames
          .asSequence()
          .take(maxNamesToLog)
          .joinToString(", ") { StringUtil.wrapWithDoubleQuote(it) }
        if (debugNames.size > maxNamesToLog) {
          reasonMessage += " and " + (debugNames.size - maxNamesToLog) + " iterators more"
        }
        logRootChanges(project, false)
        return@async ScanningIterators(
          reasonMessage,
          iterators,
          dependenciesStatusMark
        )
      }
      return@async CancelledScanning
    }
  }

  fun shouldCauseRescan(oldEntity: WorkspaceEntity?, newEntity: WorkspaceEntity?): Boolean {
    return tracker.shouldRescan(oldEntity, newEntity, project)
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
          val message =
            if (isFullReindex) "Project roots of " + project.getName() + " have changed" else "Project roots of " + project.getName() + " will be partially reindexed"
          LOG.debug(message, Throwable())
        }
      }
      else {
        ROOT_CHANGES_LOGGER.info(project, isFullReindex)
      }
    }
  }
}
