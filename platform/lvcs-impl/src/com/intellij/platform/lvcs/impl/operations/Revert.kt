// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.operations

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.integration.revertion.DifferenceReverter
import com.intellij.history.integration.revertion.Reverter
import com.intellij.history.integration.revertion.SelectionReverter
import com.intellij.openapi.project.Project
import com.intellij.platform.lvcs.impl.ActivityScope
import com.intellij.platform.lvcs.impl.RevisionSelection
import com.intellij.platform.lvcs.impl.diff.createSelectionCalculator
import com.intellij.platform.lvcs.impl.diff.diff

/**
 * @see com.intellij.history.integration.ui.models.HistoryDialogModel.createReverter
 */
internal fun ActivityScope.createReverter(project: Project,
                                          facade: LocalHistoryFacade,
                                          gateway: IdeaGateway,
                                          selection: RevisionSelection): Reverter {
  if (this is ActivityScope.Selection) {
    val calculator = createSelectionCalculator(gateway, selection)
    return SelectionReverter(project, facade, gateway, calculator, selection.leftRevision, selection.rightEntry, from, to)
  }
  return DifferenceReverter(project, facade, gateway, selection.diff, selection.leftRevision)
}