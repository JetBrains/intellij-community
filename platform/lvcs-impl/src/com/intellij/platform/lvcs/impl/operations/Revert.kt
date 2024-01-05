// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.operations

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.history.integration.revertion.DifferenceReverter
import com.intellij.history.integration.revertion.Reverter
import com.intellij.history.integration.revertion.SelectionReverter
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.*
import com.intellij.platform.lvcs.impl.diff.createSelectionCalculator
import com.intellij.platform.lvcs.impl.diff.getDiff
import com.intellij.platform.lvcs.impl.diff.getEntryPath
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal fun LocalHistoryFacade.createReverter(project: Project, gateway: IdeaGateway, scope: ActivityScope, selection: ChangeSetSelection,
                                               isOldContentUsed: Boolean): Reverter? {
  val targetRevisionId = selection.leftRevision
  if (targetRevisionId == RevisionId.Current) return null

  val rootEntry = runReadAction { gateway.createTransientRootEntry() }
  val commandNameSupplier = Supplier {
    return@Supplier getRevertCommandName(selection.leftItem, isOldContentUsed)
  }

  if (scope is ActivityScope.Selection) {
    val calculator = createSelectionCalculator(gateway, scope, rootEntry, selection, isOldContentUsed)
    return SelectionReverter(project, this, gateway, calculator, targetRevisionId, getEntryPath(gateway, scope), scope.from, scope.to,
                             commandNameSupplier)
  }
  val entryPath = getEntryPath(gateway, scope)
  val diff = getDiff(rootEntry, selection, entryPath, isOldContentUsed)
  return DifferenceReverter(project, this, gateway, diff, commandNameSupplier)
}

private fun getRevertCommandName(item: ChangeSetActivityItem?, isOldContentUsed: Boolean): @Nls String? {
  if (item == null) return LocalHistoryBundle.message("system.label.revert")
  val name = item.name
  val date = DateFormatUtil.formatDateTime(item.timestamp)
  if (isOldContentUsed) {
    if (name != null) {
      return LocalHistoryBundle.message("system.label.revert.change.date", name, date)
    }
    return LocalHistoryBundle.message("system.label.revert.date", date)
  }
  if (name != null) {
    return LocalHistoryBundle.message("system.label.revert.to.change.date", name, date)
  }
  return LocalHistoryBundle.message("system.label.revert.to.date", date)
}