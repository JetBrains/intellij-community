// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.CodeReviewTitleUIUtil
import com.intellij.collaboration.ui.codereview.details.data.ReviewRequestState
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import com.intellij.collaboration.ui.util.bindTextHtmlIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.ui.PopupHandler
import com.intellij.util.ui.JBFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JEditorPane
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
    val stateTextModel = SingleValueModel<@Nls String?>(null)
    scope.launchNow {
      detailsVm.reviewRequestState.collect { reviewRequestState ->
        stateTextModel.value = ReviewDetailsUIUtil.getRequestStateText(reviewRequestState)
      }
    }

    val stateLabel = CollaborationToolsUIUtil.createTagLabel(stateTextModel).apply {
      bindVisibilityIn(scope, detailsVm.reviewRequestState.map { it != ReviewRequestState.OPENED })
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