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

abstract class CommitRenderer<T> : ListCellRenderer<T> {
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
    textPanel.removeAll()
    commitPanel.removeAll()

    allCommitsMessage.clear()
    commitMessage.clear()
    authorAndDate.clear()

    commitMessage.foreground = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
    authorAndDate.foreground = ListUiUtil.WithTallRow.secondaryForeground(isSelected, list.hasFocus())

    iconLabel.icon = if (isCommitSelected(value)) AllIcons.Actions.Checked_selected
    else JBUIScale.scaleIcon(EmptyIcon.create(EMPTY_ICON_SIZE))

    if (value == null) {
      allCommitsMessage.append(getAllCommitsText())
      textPanel.addToCenter(allCommitsMessage)
    }
    else {
      commitMessage.append(getCommitTitle(value))
      authorAndDate.append("${getAuthor(value)} ${DateFormatUtil.formatPrettyDateTime(getDate(value))}")
      textPanel.addToCenter(commitMessage).addToBottom(authorAndDate)
    }

    return commitPanel.addToLeft(iconLabel).addToCenter(textPanel).apply {
      border = if (value == null) IdeBorderFactory.createBorder(SideBorder.BOTTOM) else null
      UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus()))
    }
  }

  abstract fun isCommitSelected(value: T?): Boolean

  abstract fun getAllCommitsText(): @NlsSafe String

  abstract fun getCommitTitle(value: T): @NlsSafe String

  abstract fun getAuthor(value: T): @NlsSafe String

  abstract fun getDate(value: T): Date

  companion object {
    private const val TOP_BOTTOM_OFFSET = 4
    private const val ICON_LEFT_RIGHT_OFFSET = 8
    private const val EMPTY_ICON_SIZE = 12
  }
}