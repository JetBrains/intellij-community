// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.revisions.Difference
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.*
import com.intellij.util.containers.JBIterable

private class ActivityDiffDataImpl(override val presentableChanges: Iterable<ActivityDiffObject>) : ActivityDiffData

internal fun LocalHistoryFacade.createDiffData(gateway: IdeaGateway,
                                               scope: ActivityScope,
                                               selection: ChangeSetSelection,
                                               isOldContentUsed: Boolean): ActivityDiffData {
  val rootEntry = selection.data.getRootEntry(gateway)
  val entryPath = getEntryPath(gateway, scope)
  val differences = getDiff(rootEntry, selection, entryPath, isOldContentUsed)
  val differenceObjects = JBIterable.from(differences).filter { it.isFile }
    .mapNotNull { it.toDiffObject(gateway, scope, selection, isOldContentUsed) }
  return ActivityDiffDataImpl(differenceObjects)
}

private fun Difference.toDiffObject(gateway: IdeaGateway, scope: ActivityScope, selection: ChangeSetSelection, isOldContentUsed: Boolean): DifferenceObject? {
  val targetFilePath = filePath ?: (scope as? ActivityScope.File)?.filePath
  if (targetFilePath == null) return null
  return DifferenceObject(gateway, scope, selection, this, targetFilePath, isOldContentUsed)
}

internal fun LocalHistoryFacade.createSingleFileDiffRequestProducer(project: Project,
                                                                    gateway: IdeaGateway,
                                                                    scope: ActivityScope,
                                                                    selection: ChangeSetSelection,
                                                                    isOldContentUsed: Boolean): DiffRequestProducer {
  val loadDifference = {
    val rootEntry = selection.data.getRootEntry(gateway)
    val entryPath = getEntryPath(gateway, scope)
    getSingleFileDiff(rootEntry, selection, entryPath, isOldContentUsed)
  }
  if (scope is ActivityScope.Selection) {
    val calculator = selection.data.getSelectionCalculator(this, gateway, scope, isOldContentUsed)
    return SelectionDiffRequestProducer(project, gateway, scope, selection, loadDifference, calculator, isOldContentUsed)
  }
  return DifferenceDiffRequestProducer.WithLazyDiff(project, gateway, scope, selection, loadDifference, isOldContentUsed)
}