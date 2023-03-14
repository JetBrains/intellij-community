// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.util.RoundedCellRenderer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import java.util.*
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

class CommitRenderer<T> private constructor(private val presenter: (T?) -> SelectableWrapper<CommitPresenter>) : ListCellRenderer<T> {
  private val iconLabel = JLabel().apply {
    horizontalAlignment = SwingConstants.CENTER
    verticalAlignment = SwingConstants.TOP
    border = JBUI.Borders.empty(12, BORDER_OFFSET, 0, ICON_TEXT_OFFSET)
  }
  private val allCommitsMessage = JLabel().apply {
    border = JBUI.Borders.empty(BORDER_OFFSET, 0)
  }
  private val commitMessage = JLabel().apply {
    border = JBUI.Borders.empty(BORDER_OFFSET, 0, 0, BORDER_OFFSET)
  }
  private val authorAndDate = JLabel().apply {
    border = JBUI.Borders.empty(0, 0, BORDER_OFFSET, BORDER_OFFSET)
    foreground = NamedColorUtil.getInactiveTextColor()
  }
  private val textPanel = BorderLayoutPanel()
  private val commitPanel = BorderLayoutPanel()

  override fun getListCellRendererComponent(list: JList<out T>,
                                            value: T?,
                                            index: Int,
                                            cellSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    cleanupComponents()

    val presentation = presenter(value)
    val commit = presentation.value
    iconLabel.icon = if (presentation.isSelected) AllIcons.Actions.Checked_selected else emptyIcon

    when (commit) {
      is CommitPresenter.SingleCommit -> {
        commitMessage.text = commit.title
        authorAndDate.text = "${commit.author}, ${DateFormatUtil.formatPrettyDateTime(commit.committedDate)}"
        textPanel.addToCenter(commitMessage).addToBottom(authorAndDate)
      }
      is CommitPresenter.AllCommits -> {
        allCommitsMessage.text = commit.title
        textPanel.addToCenter(allCommitsMessage)
      }
    }

    return commitPanel.addToLeft(iconLabel).addToCenter(textPanel).apply {
      UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, cellSelected, list.hasFocus()))
    }
  }

  private fun cleanupComponents() {
    textPanel.removeAll()
    commitPanel.removeAll()
  }

  companion object {
    private const val BORDER_OFFSET = 8
    private const val ICON_TEXT_OFFSET = 6
    private const val EMPTY_ICON_SIZE = 12

    private val emptyIcon: EmptyIcon = JBUIScale.scaleIcon(EmptyIcon.create(EMPTY_ICON_SIZE))

    @JvmStatic
    fun <T> createCommitRenderer(presenter: (T?) -> SelectableWrapper<CommitPresenter>): ListCellRenderer<T> {
      var commitRenderer: ListCellRenderer<T> = CommitRenderer(presenter)
      if (ExperimentalUI.isNewUI()) {
        commitRenderer = RoundedCellRenderer(commitRenderer, false)
      }
      return GroupedRenderer(commitRenderer, hasSeparatorBelow = { value, _ ->
        presenter(value).value is CommitPresenter.AllCommits
      })
    }
  }
}

sealed interface CommitPresenter {
  class SingleCommit(
    val title: @NlsSafe String,
    val author: @NlsSafe String,
    val committedDate: Date
  ) : CommitPresenter

  class AllCommits(
    val title: @NlsSafe String
  ) : CommitPresenter
}

data class SelectableWrapper<T>(val value: T, var isSelected: Boolean = false)