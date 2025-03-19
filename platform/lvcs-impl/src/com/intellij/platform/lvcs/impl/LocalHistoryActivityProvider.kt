// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.platform.lvcs.impl.diff.createDiffData
import com.intellij.platform.lvcs.impl.diff.createSingleFileDiffRequestProducer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.jetbrains.annotations.ApiStatus

internal const val USE_OLD_CONTENT = true

internal class LocalHistoryActivityProvider(val project: Project, private val gateway: IdeaGateway) : ActivityProvider {
  private val facade = LocalHistoryImpl.getInstanceImpl().facade!!

  override fun getActivityItemsChanged(scope: ActivityScope): Flow<Unit> {
    return facade.onChangeSetFinished(project, gateway, scope)
  }

  override fun loadActivityList(scope: ActivityScope, fileFilter: String?): ActivityData {
    gateway.registerUnsavedDocuments(facade)

    val filter = HistoryPathFilter.create(fileFilter, project)
    val projectId = project.locationHash
    if (scope is ActivityScope.File) {
      return loadFileActivityList(projectId, scope, filter)
    } else if (scope is ActivityScope.Files) {
      return loadFilesActivityList(projectId, scope, filter)
    }
    return loadRecentActivityList(projectId, scope, filter)
  }

  private fun loadFileActivityList(projectId: String, scope: ActivityScope.File, scopeFilter: HistoryPathFilter?): ActivityData {
    val path = gateway.getPathOrUrl(scope.file)
    val activityItems = mutableListOf<ActivityItem>()
    val affectedPaths = mutableSetOf(path)

    doLoadPathActivityList(projectId, scope, path, scopeFilter, affectedPaths, activityItems)

    return ActivityData(activityItems).also { it.putUserData(AFFECTED_PATHS, affectedPaths) }
  }

  private fun loadFilesActivityList(projectId: String, scope: ActivityScope.Files, scopeFilter: HistoryPathFilter?): ActivityData {
    val paths = scope.files.map { gateway.getPathOrUrl(it) }
    val activityItems = mutableListOf<ActivityItem>()
    val affectedPaths = paths.toMutableSet()

    for (path in paths) {
      doLoadPathActivityList(projectId, scope, path, scopeFilter, affectedPaths, activityItems)
    }

    return ActivityData(activityItems.toSet().sortedByDescending { it.timestamp }).also { it.putUserData(AFFECTED_PATHS, affectedPaths) }
  }

  private fun doLoadPathActivityList(projectId: String, scope: ActivityScope, path: String, scopeFilter: HistoryPathFilter?,
                                     affectedPaths: MutableSet<String>, activityItems: MutableList<ActivityItem>) {
    var lastEventLabel: ChangeSet? = null
    val userLabels = mutableListOf<ChangeSet>()
    facade.collectChanges(path, ChangeAndPathProcessor(projectId, scopeFilter, affectedPaths::add) { changeSet ->
      if (changeSet.isSystemLabelOnly) return@ChangeAndPathProcessor
      if (changeSet.isLabelOnly) {
        if (changeSet.activityId == CommonActivity.UserLabel) {
          userLabels.add(changeSet)
          lastEventLabel = null
        } else {
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

  private fun loadRecentActivityList(projectId: String, scope: ActivityScope, fileFilter: HistoryPathFilter?): ActivityData {
    val result = mutableListOf<ActivityItem>()
    val paths = project.getBaseDirectories().map { gateway.getPathOrUrl(it) }
    for (changeSet in facade.changes) {
      if (changeSet.isSystemLabelOnly) continue
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

  override fun filterActivityList(scope: ActivityScope, data: ActivityData, contentFilter: String?): Set<ActivityItem>? {
    val changeSets = data.getChangeSets()
    if (contentFilter.isNullOrEmpty() || changeSets.isEmpty()) return null
    val fileScope = scope as? ActivityScope.File ?: return null

    val filteredIds = mutableSetOf<Long>()
    val processor: (Long, String?) -> Boolean = { changeSetId, content ->
      if (content?.contains(contentFilter, true) == true) filteredIds.add(changeSetId)
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

    return data.items.filterTo(mutableSetOf()) { (it is ChangeSetActivityItem) && filteredIds.contains(it.id) }
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
      is LabelActivityItem -> ActivityPresentation(item.name ?: "", icon)
      else -> null
    }
  }
}

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