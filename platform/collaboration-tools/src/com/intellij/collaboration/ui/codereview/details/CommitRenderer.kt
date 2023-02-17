// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import java.util.*
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class CommitRenderer<T>(private val presenter: (T?) -> CommitPresenter) : ListCellRenderer<T> {
  private val iconLabel = JLabel().apply {
    border = JBUI.Borders.empty(0, ICON_LEFT_RIGHT_OFFSET)
  }
  private val allCommitsMessage = SimpleColoredComponent().apply {
    border = JBUI.Borders.empty(TOP_BOTTOM_OFFSET, 0)
  }
  private val commitMessage = SimpleColoredComponent().apply {
    border = JBUI.Borders.emptyTop(TOP_BOTTOM_OFFSET)
  }
  private val authorAndDate = SimpleColoredComponent().apply {
    border = JBUI.Borders.emptyBottom(TOP_BOTTOM_OFFSET)
  }
  private val textPanel = BorderLayoutPanel()
  private val commitPanel = BorderLayoutPanel()

  override fun getListCellRendererComponent(list: JList<out T>,
                                            value: T?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    cleanupComponents()

    commitMessage.foreground = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    authorAndDate.foreground = ListUiUtil.WithTallRow.secondaryForeground(isSelected, list.hasFocus())

    val presentation = presenter(value)
    iconLabel.icon = if (presentation.isSelected) AllIcons.Actions.Checked_selected else emptyIcon

    when (presentation) {
      is CommitPresenter.SingleCommit -> {
        commitMessage.append(presentation.title)
        authorAndDate.append("${presentation.author} ${DateFormatUtil.formatPrettyDateTime(presentation.date)}")
        textPanel.addToCenter(commitMessage).addToBottom(authorAndDate)
      }
      is CommitPresenter.AllCommits -> {
        allCommitsMessage.append(presentation.title)
        textPanel.addToCenter(allCommitsMessage)
      }
    }

    return commitPanel.addToLeft(iconLabel).addToCenter(textPanel).apply {
      border = if (presentation is CommitPresenter.AllCommits) IdeBorderFactory.createBorder(SideBorder.BOTTOM) else null
      UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus()))
    }
  }

  private fun cleanupComponents() {
    textPanel.removeAll()
    commitPanel.removeAll()

    allCommitsMessage.clear()
    commitMessage.clear()
    authorAndDate.clear()
  }

  companion object {
    private const val TOP_BOTTOM_OFFSET = 4
    private const val ICON_LEFT_RIGHT_OFFSET = 8
    private const val EMPTY_ICON_SIZE = 12

    private val emptyIcon: EmptyIcon = JBUIScale.scaleIcon(EmptyIcon.create(EMPTY_ICON_SIZE))
  }
}

sealed interface CommitPresenter {
  val title: @NlsSafe String
  val isSelected: Boolean

  class SingleCommit(
    override val title: @NlsSafe String,
    override val isSelected: Boolean,
    val author: @NlsSafe String,
    val date: Date
  ) : CommitPresenter

  class AllCommits(
    override val title: @NlsSafe String,
    override val isSelected: Boolean
  ) : CommitPresenter
}