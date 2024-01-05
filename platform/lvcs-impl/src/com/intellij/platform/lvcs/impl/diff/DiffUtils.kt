// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.tree.Entry
import com.intellij.history.core.tree.RootEntry
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.ui.models.DirectoryChangeModel
import com.intellij.history.integration.ui.models.SelectionCalculator
import com.intellij.history.integration.ui.views.DirectoryChange
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vcs.changes.Change
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

internal fun LocalHistoryFacade.getDiff(rootEntry: RootEntry,
                                        selection: ChangeSetSelection,
                                        entryPath: String,
                                        isOldContentUsed: Boolean): List<Difference> {
  val leftEntry = findEntry(rootEntry, selection.leftRevision, entryPath, isOldContentUsed)
  val rightEntry = findEntry(rootEntry, selection.rightRevision, entryPath, isOldContentUsed)
  return Entry.getDifferencesBetween(leftEntry, rightEntry, selection.rightRevision is RevisionId.Current)
}

internal fun LocalHistoryFacade.getDiff(gateway: IdeaGateway,
                                        scope: ActivityScope,
                                        selection: ChangeSetSelection,
                                        isOldContentUsed: Boolean): List<Difference> {
  val rootEntry = runReadAction { gateway.createTransientRootEntry() }
  val entryPath = getEntryPath(gateway, scope)
  return getDiff(rootEntry, selection, entryPath, isOldContentUsed).toList()
}

internal fun getEntryPath(gateway: IdeaGateway, scope: ActivityScope): String {
  return if (scope is ActivityScope.File) gateway.getPathOrUrl(scope.file) else ""
}

internal fun getChanges(gateway: IdeaGateway, scope: ActivityScope, diff: List<Difference>): List<Change> {
  return diff.map { difference ->
    if (scope is ActivityScope.Directory) {
      return@map DirectoryChange(DirectoryChangeModel(difference, gateway))
    }
    return@map Change(difference.getLeftContentRevision(gateway), difference.getRightContentRevision(gateway))
  }
}

internal fun LocalHistoryFacade.createSelectionCalculator(gateway: IdeaGateway, scope: ActivityScope.Selection, rootEntry: RootEntry,
                                                          selection: ChangeSetSelection, isOldContentUsed: Boolean): SelectionCalculator {
  val entryPath = getEntryPath(gateway, scope)
  val changeSets = selection.allChangeSets.map { RevisionId.ChangeSet(it.id) }
  return object : SelectionCalculator(gateway, listOf(RevisionId.Current) + changeSets, scope.from, scope.to) {
    override fun getEntry(revision: RevisionId): Entry? {
      return findEntry(rootEntry, revision, entryPath, isOldContentUsed)
    }
  }
}