// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.tree.Entry
import com.intellij.history.core.tree.RootEntry
import com.intellij.history.integration.IdeaGateway
import com.intellij.platform.lvcs.impl.*

internal fun LocalHistoryFacade.findEntry(rootEntry: RootEntry, revisionId: RevisionId, entryPath: String, before: Boolean): Entry? {
  return when (revisionId) {
    is RevisionId.ChangeSet -> {
      val rootCopy = rootEntry.copy()
      val entryPathInChangeSet = revertUpToChangeSet(rootCopy, revisionId.id, entryPath, before, true)
      rootCopy.findEntry(entryPathInChangeSet)
    }
    RevisionId.Current -> rootEntry.findEntry(entryPath)
  }
}

internal fun LocalHistoryFacade.getSingleFileDiff(rootEntry: RootEntry,
                                                  selection: ChangeSetSelection,
                                                  entryPath: String,
                                                  isOldContentUsed: Boolean): Difference {
  val leftEntry = findEntry(rootEntry, selection.leftRevision, entryPath, isOldContentUsed)
  val rightEntry = findEntry(rootEntry, selection.rightRevision, entryPath, isOldContentUsed)
  return Difference(leftEntry, rightEntry, selection.rightRevision is RevisionId.Current)
}

internal fun LocalHistoryFacade.getDiff(rootEntry: RootEntry,
                                        selection: ChangeSetSelection,
                                        entryPaths: Collection<String>,
                                        diffMode: DirectoryDiffMode,
                                        isOldContentUsed: Boolean): List<Difference> {
  val leftRevision = selection.leftRevision
  val rightRevision = when (diffMode) {
    DirectoryDiffMode.WithLocal -> selection.rightRevision
    DirectoryDiffMode.WithNext -> {
      val rightItem = selection.rightItem
      if (rightItem != null) RevisionId.ChangeSet(rightItem.id) else nextRevision(leftRevision)
    }
  }
  return entryPaths.flatMap {
    val leftEntry = findEntry(rootEntry, leftRevision, it, isOldContentUsed)
    val rightEntry = findEntry(rootEntry, rightRevision, it, isOldContentUsed)
    Entry.getDifferencesBetween(leftEntry, rightEntry, rightRevision is RevisionId.Current)
  }
}

private fun LocalHistoryFacade.nextRevision(revisionId: RevisionId): RevisionId {
  if (revisionId is RevisionId.Current) return RevisionId.Current
  revisionId as RevisionId.ChangeSet

  var nextChange: Long? = null
  for (change in changes) {
    if (change.id == revisionId.id) break
    nextChange = change.id
  }
  return nextChange?.let { RevisionId.ChangeSet(it) } ?: RevisionId.Current
}

internal fun LocalHistoryFacade.getDiff(gateway: IdeaGateway,
                                        scope: ActivityScope,
                                        selection: ChangeSetSelection,
                                        diffMode: DirectoryDiffMode,
                                        isOldContentUsed: Boolean): List<Difference> {
  val rootEntry = selection.data.getRootEntry(gateway)
  val entryPaths = getEntryPaths(gateway, scope)
  return getDiff(rootEntry, selection, entryPaths, diffMode, isOldContentUsed).toList()
}

internal fun getEntryPaths(gateway: IdeaGateway, scope: ActivityScope): Collection<String> {
  return when (scope) {
    is ActivityScope.File -> listOf(getEntryPath(gateway, scope))
    is ActivityScope.Files -> scope.files.map { gateway.getPathOrUrl(it) }
    else -> listOf("")
  }
}

internal fun getEntryPath(gateway: IdeaGateway, scope: ActivityScope.File) = gateway.getPathOrUrl(scope.file)