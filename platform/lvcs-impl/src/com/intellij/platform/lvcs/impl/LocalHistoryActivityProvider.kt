// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.RevisionsCollector
import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.revisions.Revision
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.history.integration.ui.models.filterContents
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val USE_OLD_CONTENT = true

internal class LocalHistoryActivityProvider(val project: Project, private val gateway: IdeaGateway) : ActivityProvider {
  private val facade = LocalHistoryImpl.getInstanceImpl().facade!!

  override val activityItemsChanged: Flow<Unit>
    get() = facade.onChangeSetFinished()

  override fun loadActivityList(scope: ActivityScope, scopeFilter: String?): List<ActivityItem> {
    if (scope is ActivityScope.File) {
      return runReadAction {
        gateway.registerUnsavedDocuments(facade)
        return@runReadAction RevisionsCollector.collect(facade, gateway.createTransientRootEntry(),
                                                        gateway.getPathOrUrl(scope.file),
                                                        project.getLocationHash(), scopeFilter, USE_OLD_CONTENT)
      }.map {
        RevisionActivityItem(it)
      }
    }
    return facade.getRecentChanges(runReadAction { gateway.createTransientRootEntry() }).map { RecentChangeActivityItem(it) }
  }

  override fun filterActivityList(scope: ActivityScope, items: List<ActivityItem>, activityFilter: String?): Set<ActivityItem>? {
    val revisions = items.mapNotNull { (it as? RevisionActivityItem)?.revision }
    if (activityFilter.isNullOrEmpty() || revisions.isEmpty()) return null
    val fileScope = scope as? ActivityScope.File ?: return null

    val revisionIds = facade.filterContents(gateway, fileScope.file, revisions, activityFilter, before = USE_OLD_CONTENT)
    return items.filterTo(mutableSetOf()) { (it is RevisionActivityItem) && revisionIds.contains(it.revision.changeSetId) }
  }

  override fun loadDiffData(scope: ActivityScope, selection: ActivitySelection): ActivityDiffData? {
    val revisionSelection = selection.toRevisionSelection(scope) ?: return null
    val differences = if (scope is ActivityScope.SingleFile || scope is ActivityScope.Selection) {
      listOf(Difference(revisionSelection.leftEntry, revisionSelection.rightEntry, revisionSelection.rightRevision.isCurrent))
    }
    else {
      revisionSelection.diff
    }
    return ActivityDiffDataWithDifferences(gateway, scope, revisionSelection, differences)
  }

  override fun isScopeFilterSupported(scope: ActivityScope): Boolean {
    return scope is ActivityScope.Directory
  }

  override fun isActivityFilterSupported(scope: ActivityScope): Boolean {
    return scope is ActivityScope.SingleFile || scope is ActivityScope.Selection
  }

  override fun getPresentation(item: ActivityItem): ActivityPresentation? {
    return when (item) {
      is RevisionActivityItem -> item.revision.createPresentation()
      is RecentChangeActivityItem -> item.recentChange.revisionAfter.createPresentation()
      else -> null
    }
  }
}

private fun LocalHistoryFacade.onChangeSetFinished(): Flow<Unit> {
  return callbackFlow {
    val listenerDisposable = Disposer.newDisposable()
    this@onChangeSetFinished.addListener(object : LocalHistoryFacade.Listener() {
      override fun changeSetFinished() {
        trySend(Unit)
      }
    }, listenerDisposable)
    trySend(Unit)
    awaitClose { Disposer.dispose(listenerDisposable) }
  }
}

private fun Revision.createPresentation(): ActivityPresentation {
  val text = if (isLabel) label ?: ""
  else changeSetName ?: (StringUtil.shortenTextWithEllipsis(affectedFileNames.first.joinToString(), 80, 0))
  return ActivityPresentation(text)
}