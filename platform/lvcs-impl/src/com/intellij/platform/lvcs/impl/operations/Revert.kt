// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.operations

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.revisions.Difference
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.history.integration.revertion.DifferenceReverter
import com.intellij.history.integration.revertion.Reverter
import com.intellij.history.integration.revertion.SelectionReverter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ChangeSetActivityItem
import com.intellij.platform.lvcs.impl.ChangeSetSelection
import com.intellij.platform.lvcs.impl.DirectoryDiffMode
import com.intellij.platform.lvcs.impl.RevisionId
import com.intellij.platform.lvcs.impl.diff.getDiff
import com.intellij.platform.lvcs.impl.diff.getEntryPath
import com.intellij.platform.lvcs.impl.getSelectionCalculator
import com.intellij.platform.lvcs.impl.leftRevision
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal fun LocalHistoryFacade.createReverter(project: Project, gateway: IdeaGateway, scope: ActivityScope, selection: ChangeSetSelection,
                                               diffMode: DirectoryDiffMode, isOldContentUsed: Boolean): Reverter? {
  val targetRevisionId = selection.leftRevision
  if (targetRevisionId == RevisionId.Current) return null

  val commandNameSupplier = Supplier {
    return@Supplier getRevertCommandName(selection.leftItem, isOldContentUsed)
  }

  if (scope is ActivityScope.Selection) {
    val calculator = selection.data.getSelectionCalculator(this, gateway, scope, isOldContentUsed)
    return SelectionReverter(project, this, gateway, calculator, targetRevisionId, getEntryPath(gateway, scope), scope.from, scope.to,
                             commandNameSupplier)
  }

  val diff = getDiff(gateway, scope, selection, diffMode, isOldContentUsed)
  return DifferenceReverter(project, this, gateway, diff, commandNameSupplier)
}

internal fun LocalHistoryFacade.createDifferenceReverter(project: Project, gateway: IdeaGateway, selection: ChangeSetSelection, differences: List<Difference>,
                                                         isOldContentUsed: Boolean): Reverter? {
  val targetRevisionId = selection.leftRevision
  if (targetRevisionId == RevisionId.Current) return null
  if (differences.isEmpty()) return null
  return DifferenceReverter(project, this, gateway, differences) {
    getRevertCommandName(selection.leftItem, isOldContentUsed)
  }
}

private fun getRevertCommandName(item: ChangeSetActivityItem?, isOldContentUsed: Boolean): @Nls String? {
  if (item == null) return LocalHistoryBundle.message("activity.name.revert")
  return getRevertCommandName(item.name, item.timestamp, isOldContentUsed)
}

internal fun getRevertCommandName(name: @NlsSafe String?, timestamp: Long, isOldContentUsed: Boolean): @Nls String? {
  val date = DateFormatUtil.formatDateTime(timestamp)
  if (isOldContentUsed) {
    if (name != null) {
      return LocalHistoryBundle.message("activity.name.revert.change.date", name, date)
    }
    return LocalHistoryBundle.message("activity.name.revert.date", date)
  }
  if (name != null) {
    return LocalHistoryBundle.message("activity.name.revert.to.change.date", name, date)
  }
  return LocalHistoryBundle.message("activity.name.revert.to.date", date)
}