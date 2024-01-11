// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.revisions.Revision.getDifferencesBetween
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.ui.models.DirectoryChangeModel
import com.intellij.history.integration.ui.models.SelectionCalculator
import com.intellij.history.integration.ui.views.DirectoryChange
import com.intellij.openapi.vcs.changes.Change
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.RevisionSelection
import com.intellij.platform.lvcs.impl.createCurrentRevision
import com.intellij.util.containers.JBIterable

/**
 * @see com.intellij.history.integration.ui.models.HistoryDialogModel.getDifferences
 */
internal val RevisionSelection.diff: List<Difference>
  get() = getDifferencesBetween(leftRevision, rightRevision)

/**
 * @see com.intellij.history.integration.ui.models.HistoryDialogModel.createChange
 */
private fun Difference.getChange(gateway: IdeaGateway, scope: ActivityScope): Change {
  if (scope is ActivityScope.Directory) {
    return DirectoryChange(DirectoryChangeModel(this, gateway))
  }
  return Change(getLeftContentRevision(gateway), getRightContentRevision(gateway))
}

internal fun RevisionSelection.getChanges(gateway: IdeaGateway, scope: ActivityScope): Iterable<Change> {
  return JBIterable.from(diff).map { d -> d.getChange(gateway, scope) }
}

internal fun ActivityScope.Selection.createSelectionCalculator(gateway: IdeaGateway, selection: RevisionSelection): SelectionCalculator {
  return SelectionCalculator(gateway, listOf(createCurrentRevision(selection)) + selection.allRevisions, from, to)
}