// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.diff

import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.history.core.revisions.Difference
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.ui.models.RevisionProcessingProgress
import com.intellij.history.integration.ui.models.SelectionCalculator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.ChangeSetSelection
import com.intellij.platform.lvcs.impl.leftRevision
import com.intellij.platform.lvcs.impl.rightRevision

internal class SelectionDifferenceObject(gateway: IdeaGateway,
                                         override val scope: ActivityScope.Selection,
                                         selection: ChangeSetSelection,
                                         difference: Difference,
                                         private val selectionCalculator: SelectionCalculator,
                                         isOldContentUsed: Boolean) :
  DifferenceObject(gateway, scope, selection, difference, isOldContentUsed) {
  override fun getFileStatus(): FileStatus {
    val isLeftContentAvailable = difference.left != null && selectionCalculator.canCalculateFor(selection.leftRevision, RevisionProcessingProgress.EMPTY)
    val isRightContentAvailable = difference.right != null && selectionCalculator.canCalculateFor(selection.rightRevision, RevisionProcessingProgress.EMPTY)
    return fileStatus(isLeftContentAvailable, isRightContentAvailable)
  }

  override fun createProducer(project: Project?): DiffRequestProducer {
    return SelectionDiffRequestProducer(project, gateway, scope, selection, difference, selectionCalculator, isOldContentUsed)
  }
}