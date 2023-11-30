// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.setHtmlBody
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.ui.components.ActionLink
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.NamedColorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.SwingConstants

object CodeReviewDetailsCommitInfoComponentFactory {
  private const val GAP = 8

  fun <T> create(
    scope: CoroutineScope,
    commit: Flow<T?>,
    commitPresentation: (T) -> CommitPresentation,
    htmlPaneFactory: () -> JEditorPane
  ): JComponent {
    val withDetailedInfo = MutableStateFlow(false)
    fun showHideDetailsAction() {
      withDetailedInfo.value = !withDetailedInfo.value
    }

    val title = htmlPaneFactory()
    val description = htmlPaneFactory()
    val info = htmlPaneFactory().apply {
      foreground = NamedColorUtil.getInactiveTextColor()
    }
    val detailsAction = createShowHideActionLink(::showHideDetailsAction)

    val singleTitleRestriction = DimensionRestrictions.LinesHeight(title, 1)
    val titleLayout = SizeRestrictedSingleComponentLayout().apply {
      this.maxSize = singleTitleRestriction
    }
    val topPanel = createTopPanel(title, detailsAction, titleLayout)

    return VerticalListPanel(GAP).apply {
      bindVisibilityIn(scope, commit.map { it != null })
      add(topPanel)
      add(description)
      add(info)

      scope.launch {
        withDetailedInfo.collect { isDetailed ->
          description.isVisible = isDetailed
          info.isVisible = isDetailed
          titleLayout.maxSize = if (isDetailed) DimensionRestrictions.None else singleTitleRestriction
          detailsAction.text = if (isDetailed)
            CollaborationToolsBundle.message("review.details.commits.details.hide")
          else
            CollaborationToolsBundle.message("review.details.commits.details.show")
        }
      }

      scope.launch {
        commit.collect { commit: T? ->
          if (commit == null) return@collect
          val presentation = commitPresentation(commit)
          title.text = presentation.titleHtml
          description.text = presentation.descriptionHtml
          info.setHtmlBody("${presentation.author}, ${DateFormatUtil.formatPrettyDateTime(presentation.committedDate)}")
        }
      }
    }
  }

  private fun createShowHideActionLink(action: () -> Unit): ActionLink {
    return ActionLink(CollaborationToolsBundle.message("review.details.commits.details.show")) {
      action()
    }.apply {
      verticalAlignment = SwingConstants.TOP
    }
  }

  private fun createTopPanel(titlePanel: JComponent, detailsAction: ActionLink, layout: SizeRestrictedSingleComponentLayout): JComponent {
    return JPanel(BorderLayout()).apply {
      val wrappedTitle = JPanel(layout).apply {
        isOpaque = false
        add(titlePanel)
      }

      isOpaque = false
      add(wrappedTitle, BorderLayout.CENTER)
      add(detailsAction, BorderLayout.EAST)
    }
  }
}