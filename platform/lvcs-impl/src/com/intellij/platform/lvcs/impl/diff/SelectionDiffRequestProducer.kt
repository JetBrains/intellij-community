// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.revisions.Revision
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
                                            selection: RevisionSelection,
                                            difference: Difference,
                                            private val selectionCalculator: SelectionCalculator)
  : DifferenceDiffRequestProducer(project, gateway, scope, selection, difference) {

  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    val leftContent = createContent(difference.left, selection.leftRevision, selection.leftRevision.isCurrent, indicator)
    val rightContent = createContent(difference.right, selection.rightRevision, selection.rightRevision.isCurrent, indicator)

    val leftContentTitle = getTitle(selection.leftRevision)
    val rightContentTitle = getTitle(selection.rightRevision)

    return SimpleDiffRequest(name, leftContent, rightContent, leftContentTitle, rightContentTitle)
  }

  private fun createContent(entry: Entry?, revision: Revision, isCurrent: Boolean, indicator: ProgressIndicator): DiffContent {
    if (entry == null) return DiffContentFactory.getInstance().createEmpty()
    if (isCurrent || revision.changeSetId == null) return createCurrentDiffContent(project, gateway, entry.path, scope.from, scope.to)
    return createDiffContent(gateway, entry, revision.changeSetId!!, selectionCalculator, RevisionProcessingProgressAdapter(indicator))
  }
}