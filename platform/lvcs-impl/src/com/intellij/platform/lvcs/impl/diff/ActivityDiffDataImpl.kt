// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.revisions.Difference
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.platform.lvcs.impl.ActivityDiffData
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ChangeSetSelection
import com.intellij.platform.lvcs.impl.DirectoryDiffMode
import com.intellij.platform.lvcs.impl.filePath
import com.intellij.platform.lvcs.impl.getRootEntry
import com.intellij.platform.lvcs.impl.getSelectionCalculator
import com.intellij.util.containers.JBIterable

internal fun LocalHistoryFacade.createDiffData(gateway: IdeaGateway,
                                               scope: ActivityScope,
                                               selection: ChangeSetSelection,
                                               diffMode: DirectoryDiffMode,
                                               isOldContentUsed: Boolean): ActivityDiffData {
  val differences = getDiff(gateway, scope, selection, diffMode, isOldContentUsed)
  val presentableChanges = JBIterable.from(differences)
    .mapNotNull { it.toPresentableChange(gateway, scope, selection, isOldContentUsed) }
  return ActivityDiffData(presentableChanges, diffMode, selection is ChangeSetSelection.Single)
}

private fun Difference.toPresentableChange(gateway: IdeaGateway, scope: ActivityScope, selection: ChangeSetSelection, isOldContentUsed: Boolean): PresentableChange? {
  val targetFilePath = filePath ?: (scope as? ActivityScope.File)?.filePath
  if (targetFilePath == null) return null
  if (isFile) return PresentableFileDifference(gateway, scope, selection, this, targetFilePath, isOldContentUsed)
  return PresentableDifference(scope, selection, this, targetFilePath)
}

internal fun LocalHistoryFacade.createSingleFileDiffRequestProducer(project: Project,
                                                                    gateway: IdeaGateway,
                                                                    scope: ActivityScope,
                                                                    selection: ChangeSetSelection,
                                                                    isOldContentUsed: Boolean): DiffRequestProducer {
  val loadDifference = {
    val rootEntry = selection.data.getRootEntry(gateway)
    val entryPath = getEntryPaths(gateway, scope)
    getSingleFileDiff(rootEntry, selection, entryPath.single(), isOldContentUsed)
  }
  if (scope is ActivityScope.Selection) {
    val calculator = selection.data.getSelectionCalculator(this, gateway, scope, isOldContentUsed)
    return SelectionDiffRequestProducer(project, gateway, scope, selection, loadDifference, calculator, isOldContentUsed)
  }
  return DifferenceDiffRequestProducer.WithLazyDiff(project, gateway, scope, selection, loadDifference, isOldContentUsed)
}