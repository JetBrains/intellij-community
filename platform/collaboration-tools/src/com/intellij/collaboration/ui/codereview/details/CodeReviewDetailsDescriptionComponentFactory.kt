// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent
import javax.swing.JEditorPane

object CodeReviewDetailsDescriptionComponentFactory {
  private const val VISIBLE_DESCRIPTION_LINES = 2

  fun create(
    scope: CoroutineScope,
    detailsVm: CodeReviewDetailsViewModel,
    actionGroup: ActionGroup,
    showTimelineAction: (component: JComponent) -> Unit,
    htmlPaneFactory: () -> JEditorPane
  ): JComponent {
    val descriptionPanel = htmlPaneFactory().apply {
      detailsVm.description?.also { bindTextIn(scope, it) }
      PopupHandler.installPopupMenu(this, actionGroup, "CodeReviewDetailsPopup")
    }.let {
      CollaborationToolsUIUtil.wrapWithLimitedSize(it, DimensionRestrictions.LinesHeight(it, VISIBLE_DESCRIPTION_LINES))
    }
    val timelineLink = ActionLink(CollaborationToolsBundle.message("review.details.view.timeline.action")) {
      showTimelineAction(it.source as JComponent)
    }.apply {
      border = JBUI.Borders.emptyTop(4)
    }

    return VerticalListPanel().apply {
      name = "Review details description panel"

      add(descriptionPanel)
      add(timelineLink)
    }
  }
}