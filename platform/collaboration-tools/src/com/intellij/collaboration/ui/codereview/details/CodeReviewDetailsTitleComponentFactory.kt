// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.ui.codereview.CodeReviewTitleUIUtil
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import com.intellij.collaboration.ui.util.bindTextHtmlIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel

object CodeReviewDetailsTitleComponentFactory {
  fun create(
    scope: CoroutineScope,
    detailsVm: CodeReviewDetailsViewModel,
    urlTooltip: @Nls String,
    actionGroup: ActionGroup,
    htmlPaneFactory: () -> JEditorPane
  ): JComponent {
    val titleLabel = htmlPaneFactory().apply {
      name = "Review details title panel"
      font = JBFont.h2().asBold()
      bindTextHtmlIn(scope, detailsVm.title.map { title ->
        CodeReviewTitleUIUtil.createTitleText(
          title = title,
          reviewNumber = detailsVm.number,
          url = detailsVm.url,
          tooltip = urlTooltip
        )
      })
      PopupHandler.installPopupMenu(this, actionGroup, "CodeReviewDetailsPopup")
    }
    val stateLabel = JLabel().apply {
      font = JBFont.small()
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(0, 4)
      bindTextIn(scope, detailsVm.reviewRequestState.map { reviewRequestState ->
        ReviewDetailsUIUtil.getRequestStateText(reviewRequestState)
      })
    }.let {
      RoundedPanel(SingleComponentCenteringLayout(), 4).apply {
        border = JBUI.Borders.empty()
        background = UIUtil.getPanelBackground()
        bindVisibilityIn(scope, detailsVm.reviewRequestState.map { reviewRequestState ->
          reviewRequestState == ReviewRequestState.CLOSED ||
          reviewRequestState == ReviewRequestState.MERGED ||
          reviewRequestState == ReviewRequestState.DRAFT
        })
        add(it)
      }
    }

    return JPanel(MigLayout(
      LC().emptyBorders().fillX().hideMode(3),
      AC().gap("push")
    )).apply {
      isOpaque = false
      add(titleLabel)
      add(stateLabel, CC().alignY("top"))
    }
  }
}