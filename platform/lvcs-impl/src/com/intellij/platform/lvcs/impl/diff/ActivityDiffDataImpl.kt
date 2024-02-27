// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.revisions.Difference
import com.intellij.history.integration.IdeaGateway
import com.intellij.platform.lvcs.impl.*
import com.intellij.util.containers.JBIterable

private class ActivityDiffDataImpl(override val presentableChanges: Iterable<ActivityDiffObject>) : ActivityDiffData

internal fun LocalHistoryFacade.createDiffData(gateway: IdeaGateway,
                                               scope: ActivityScope,
                                               selection: ChangeSetSelection,
                                               isOldContentUsed: Boolean): ActivityDiffData {
  val rootEntry = selection.data.getRootEntry(gateway)
  val entryPath = getEntryPath(gateway, scope)
  val differences = if (scope is ActivityScope.SingleFile || scope is ActivityScope.Selection) {
    val leftEntry = findEntry(rootEntry, selection.leftRevision, entryPath, isOldContentUsed)
    val rightEntry = findEntry(rootEntry, selection.rightRevision, entryPath, isOldContentUsed)
    listOf(Difference(leftEntry, rightEntry, selection.rightRevision is RevisionId.Current))
  }
  else {
    getDiff(rootEntry, selection, entryPath, isOldContentUsed)
  }
  val differenceObjects = mapToDiffObjects(gateway, scope, selection, isOldContentUsed, differences)
  return ActivityDiffDataImpl(differenceObjects)
}

private fun LocalHistoryFacade.mapToDiffObjects(gateway: IdeaGateway,
                                                scope: ActivityScope,
                                                selection: ChangeSetSelection,
                                                isOldContentUsed: Boolean,
                                                differences: List<Difference>): Iterable<DifferenceObject> {
  val fileDifferences = JBIterable.from(differences).filter { it.isFile }
  return when (scope) {
    is ActivityScope.Selection -> {
      val calculator = selection.data.getSelectionCalculator(this, gateway, scope, isOldContentUsed)
      fileDifferences.map {
        SelectionDifferenceObject(gateway, scope, selection, it, calculator, isOldContentUsed)
      }
    }
    is ActivityScope.File -> {
      fileDifferences.map { DifferenceObject(gateway, scope, selection, it, isOldContentUsed) }
    }
    ActivityScope.Recent -> {
      fileDifferences.map { difference ->
        difference.filePath?.let {
          DifferenceObject(gateway, scope, selection, difference, it, isOldContentUsed)
        }
      }.filterNotNull()
    }
  }
}