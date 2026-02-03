// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.history.ActivityPresentationProvider
import com.intellij.history.core.*
import com.intellij.history.core.changes.ChangeSet
import com.intellij.history.core.changes.PutLabelChange
import com.intellij.history.integration.CommonActivity
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.lvcs.impl.actions.isShowSystemLabelsEnabled
import com.intellij.platform.lvcs.impl.diff.createDiffData
import com.intellij.platform.lvcs.impl.diff.createSingleFileDiffRequestProducer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.jetbrains.annotations.ApiStatus

internal const val USE_OLD_CONTENT = true

@ApiStatus.Internal
class LocalHistoryActivityProvider(val project: Project, private val gateway: IdeaGateway) : ActivityProvider {
  private val facade = LocalHistoryImpl.getInstanceImpl().facade!!

  override fun getActivityItemsChanged(scope: ActivityScope): Flow<Unit> {
    return facade.onChangeSetFinished(project, gateway, scope)
  }

  override fun loadActivityList(scope: ActivityScope, filter: ActivityFilter?): ActivityData {
    gateway.registerUnsavedDocuments(facade)

    val projectId = project.locationHash
    if (scope is ActivityScope.File) {
      return loadFileActivityList(projectId, scope, filter)
    } else if (scope is ActivityScope.Files) {
      return loadFilesActivityList(projectId, scope, filter)
    }
    return loadRecentActivityList(projectId, scope, filter)
  }

  private fun loadFileActivityList(projectId: String, scope: ActivityScope.File, filter: ActivityFilter?): ActivityData {
    val path = gateway.getPathOrUrl(scope.file)
    val activityItems = mutableListOf<ActivityItem>()
    val affectedPaths = mutableSetOf(path)

    doLoadPathActivityList(projectId, scope, path, affectedPaths, activityItems, filter)

    return ActivityData(activityItems).also { it.putUserData(AFFECTED_PATHS, affectedPaths) }
  }

  private fun loadFilesActivityList(projectId: String, scope: ActivityScope.Files, filter: ActivityFilter?): ActivityData {
    val paths = scope.files.map { gateway.getPathOrUrl(it) }
    val activityItems = mutableListOf<ActivityItem>()
    val affectedPaths = paths.toMutableSet()

    for (path in paths) {
      doLoadPathActivityList(projectId, scope, path,  affectedPaths, activityItems, filter)
    }

    return ActivityData(activityItems.toSet().sortedByDescending { it.timestamp }).also { it.putUserData(AFFECTED_PATHS, affectedPaths) }
  }

  private fun doLoadPathActivityList(
    projectId: String, scope: ActivityScope, path: String,
    affectedPaths: MutableSet<String>, activityItems: MutableList<ActivityItem>, filter: ActivityFilter?,
  ) {
    var lastEventLabel: ChangeSet? = null
    val userLabels = mutableListOf<ChangeSet>()
    val showSystemLabels = filter.showSystemLabels()

    facade.collectChanges(path, ChangeAndPathProcessor(projectId, filter?.filePathFilter, affectedPaths::add) { changeSet ->
      if (!showSystemLabels && changeSet.isSystemLabelOnly) return@ChangeAndPathProcessor
      if (changeSet.isLabelOnly) {
        if (changeSet.activityId == CommonActivity.UserLabel) {
          userLabels.add(changeSet)
          if (showSystemLabels && lastEventLabel != null) activityItems.add(lastEventLabel!!.toActivityItem(scope))
          lastEventLabel = null
        } else {
          if (showSystemLabels && lastEventLabel != null) activityItems.add(lastEventLabel!!.toActivityItem(scope))
          lastEventLabel = changeSet
        }
      }
      else {
        if (userLabels.isNotEmpty()) activityItems.addAll(userLabels.map { it.toActivityItem(scope) })
        if (lastEventLabel != null) activityItems.add(lastEventLabel!!.toActivityItem(scope))

        userLabels.clear()
        lastEventLabel = null

        activityItems.add(changeSet.toActivityItem(scope))
      }
    })
  }

  private fun loadRecentActivityList(projectId: String, scope: ActivityScope, filter: ActivityFilter?): ActivityData {
    val result = mutableListOf<ActivityItem>()
    val paths = project.getBaseDirectories().map { gateway.getPathOrUrl(it) }
    val fileFilter = filter?.filePathFilter
    val showSystemLabels = filter.showSystemLabels()

    for (changeSet in facade.changes) {
      if (!showSystemLabels && changeSet.isSystemLabelOnly) continue
      if (changeSet.isLabelOnly) {
        if (fileFilter != null || !changeSet.changes.any { it.affectsProject(projectId) }) continue
      }
      else {
        if (!changeSet.changes.any { change ->
            change !is PutLabelChange && paths.any { path -> change.matches(projectId, path, fileFilter) }
          }) continue
      }
      result.add(changeSet.toActivityItem(scope))
    }
    return ActivityData(result).also { it.putUserData(AFFECTED_PATHS, paths) }
  }

  override fun filterActivityList(scope: ActivityScope, data: ActivityData, filter: ActivityFilter?): Set<ActivityItem>? {
    val changeSets = data.getChangeSets()
    if (changeSets.isEmpty()) return null
    val fileScope = scope as? ActivityScope.File ?: return null
    val contentFilter = filter?.contentFilter
    val showSystemLabels = filter.showSystemLabels()

    val filteredIds = mutableSetOf<Long>()
    val processor: (Long, String?) -> Boolean = { changeSetId, content ->
      if (contentFilter == null || content?.contains(contentFilter, true) == true) filteredIds.add(changeSetId)
      true
    }

    if (fileScope is ActivityScope.Selection) {
      data.getSelectionCalculator(facade, gateway, fileScope, USE_OLD_CONTENT).processContents(processor)
    }
    else {
      val rootEntry = data.getRootEntry(gateway).copy()
      val path = gateway.getPathOrUrl(fileScope.file)
      facade.processContents(gateway, rootEntry, path, changeSets, before = USE_OLD_CONTENT, processor = processor)
    }

    val result = mutableSetOf<ActivityItem>()
    for (item in data.items) {
      when {
        !showSystemLabels && item is ChangeSetActivityItem && item.isSystemLabelOnly() -> continue
        item is ChangeSetActivityItem && filteredIds.contains(item.id) -> result.add(item)
      }
    }
    return result
  }

  override fun loadDiffData(scope: ActivityScope, selection: ActivitySelection, diffMode: DirectoryDiffMode): ActivityDiffData? {
    val changeSetSelection = selection.toChangeSetSelection() ?: return null
    return facade.createDiffData(gateway, scope, changeSetSelection, diffMode, USE_OLD_CONTENT)
  }

  override fun loadSingleDiff(scope: ActivityScope, selection: ActivitySelection): DiffRequestProducer? {
    val changeSetSelection = selection.toChangeSetSelection() ?: return null
    return facade.createSingleFileDiffRequestProducer(project, gateway, scope, changeSetSelection, USE_OLD_CONTENT)
  }

  override fun getSupportedFilterKindFor(scope: ActivityScope): FilterKind {
    if (scope.hasMultipleFiles) return FilterKind.FILE
    return FilterKind.CONTENT
  }

  override fun getPresentation(item: ActivityItem): ActivityPresentation? {
    if (item !is ChangeSetActivityItem) return null

    val activityId = item.activityId
    val provider = activityId?.let { ACTIVITY_PRESENTATION_PROVIDER_EP.findFirstSafe { it.id == activityId.providerId } }
    val icon = provider?.getIcon(activityId.kind)
    return when (item) {
      is ChangeActivityItem -> ActivityPresentation(item.name ?: "", icon)
      is LabelActivityItem -> ActivityPresentation(item.name ?: "", icon, item.color)
      else -> null
    }
  }
}

private fun ActivityFilter?.showSystemLabels(): Boolean = this?.showSystemLabels ?: isShowSystemLabelsEnabled()

private fun LocalHistoryFacade.onChangeSetFinished(project: Project, gateway: IdeaGateway, scope: ActivityScope): Flow<Unit> {
  val condition: (ChangeSet) -> Boolean
  if (scope is ActivityScope.File) {
    val path = gateway.getPathOrUrl(scope.file)
    condition = { changeSet -> changeSet.anyChangeMatches { change -> change.matches(project.locationHash, path, null) } }
  }
  else {
    condition = { true }
  }

  return callbackFlow {
    val listenerDisposable = Disposer.newDisposable()
    this@onChangeSetFinished.addListener(object : LocalHistoryFacade.Listener() {
      override fun changeSetFinished(changeSet: ChangeSet) {
        if (condition(changeSet)) {
          trySend(Unit)
        }
      }
    }, listenerDisposable)
    trySend(Unit)
    awaitClose { Disposer.dispose(listenerDisposable) }
  }
}

val ACTIVITY_PRESENTATION_PROVIDER_EP = ExtensionPointName.create<ActivityPresentationProvider>("com.intellij.history.activityPresentationProvider")

@ApiStatus.Internal
fun ActivityData.getChangeSets(): Set<Long> {
  return items.filterIsInstance<ChangeSetActivityItem>().mapTo(mutableSetOf()) { it.id }
}
