// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.ui.models.SelectionCalculator
import com.intellij.history.integration.ui.views.RevisionProcessingProgressAdapter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.platform.lvcs.impl.*

internal class SelectionDiffRequestProducer(project: Project?,
                                            gateway: IdeaGateway,
                                            override val scope: ActivityScope.Selection,
                                            selection: ChangeSetSelection,
                                            difference: Difference,
                                            private val selectionCalculator: SelectionCalculator,
                                            isOldContentUsed: Boolean)
  : DifferenceDiffRequestProducer(project, gateway, scope, selection, difference, isOldContentUsed) {

  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    val leftContent = createContent(difference.left, selection.leftRevision, indicator)
    val rightContent = createContent(difference.right, selection.rightRevision, indicator)

    val leftContentTitle = getTitle(selection.leftItem)
    val rightContentTitle = getTitle(selection.rightItem)

    return SimpleDiffRequest(name, leftContent, rightContent, leftContentTitle, rightContentTitle)
  }

  private fun createContent(entry: Entry?, revision: RevisionId, indicator: ProgressIndicator): DiffContent {
    if (entry == null) return DiffContentFactory.getInstance().createEmpty()
    if (revision is RevisionId.ChangeSet) return createDiffContent(gateway, entry, revision.id, selectionCalculator,
                                                                   RevisionProcessingProgressAdapter(indicator))
    return createCurrentDiffContent(project, gateway, entry.path, scope.from, scope.to)
  }
}