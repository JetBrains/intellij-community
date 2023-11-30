// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.util.RoundedCellRenderer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import org.intellij.lang.annotations.Language
import java.awt.Component
import java.util.*
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class CommitRenderer<T> private constructor(
  private val commitsCount: Int,
  private val presenter: (T?) -> SelectableWrapper<CommitPresentation?>
) : ListCellRenderer<T> {

  private val allCommitsMessage: JLabel = JLabel().apply {
    border = JBUI.Borders.empty(ALL_COMMITS_TOP_BOTTOM, LEFT_RIGHT_GAP)
    iconTextGap = JBUIScale.scale(ICON_TEXT_OFFSET)
    text = CollaborationToolsBundle.message("review.details.commits.popup.all", commitsCount)
  }

  private val commitMessageIcon: JLabel = JLabel()
  private val commitMessageText: JBLabel = JBLabel().apply {
    setCopyable(true)
  }

  private val commitMessagePanel: JPanel = HorizontalListPanel(ICON_TEXT_OFFSET)

  private val authorAndDate: JLabel = JLabel().apply {
    border = JBUI.Borders.emptyTop(MESSAGE_INFO_VERTICAL_GAP)
    iconTextGap = JBUIScale.scale(ICON_TEXT_OFFSET)
    foreground = NamedColorUtil.getInactiveTextColor()
  }

  private val textPanel: BorderLayoutPanel = BorderLayoutPanel().apply {
    border = JBUI.Borders.empty(COMMIT_TOP_BOTTOM, LEFT_RIGHT_GAP)
  }

  private val commitPanel: BorderLayoutPanel = BorderLayoutPanel()

  override fun getListCellRendererComponent(list: JList<out T?>,
                                            value: T?,
                                            index: Int,
                                            cellSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    cleanupComponents()

    val presentation = presenter(value)
    val commit = presentation.value

    commitMessageIcon.icon = if (presentation.isSelected) AllIcons.Actions.Checked_selected else emptyIcon
    allCommitsMessage.icon = if (presentation.isSelected) AllIcons.Actions.Checked_selected else emptyIcon
    authorAndDate.icon = emptyIcon

    if (commit == null) {
      commitPanel.addToCenter(allCommitsMessage)
    }
    else {
      commitMessageText.text = commit.titleHtml
      authorAndDate.text = "${commit.author}, ${DateFormatUtil.formatPrettyDateTime(commit.committedDate)}"

      commitMessagePanel.add(commitMessageIcon)
      commitMessagePanel.add(commitMessageText)
      textPanel.addToCenter(commitMessagePanel).addToBottom(authorAndDate)
      commitPanel.addToCenter(textPanel)
    }

    return commitPanel.apply {
      UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, cellSelected, list.hasFocus()))
    }
  }

  private fun cleanupComponents() {
    commitMessagePanel.removeAll()
    textPanel.removeAll()
    commitPanel.removeAll()
  }

  companion object {
    private const val ALL_COMMITS_TOP_BOTTOM = 4
    private const val COMMIT_TOP_BOTTOM = 8
    private val LEFT_RIGHT_GAP: Int
      get() = CollaborationToolsUIUtil.getSize(oldUI = 8, newUI = 0) // in case of the newUI gap handled by SelectablePanel

    private const val ICON_TEXT_OFFSET = 6
    private const val MESSAGE_INFO_VERTICAL_GAP = 4

    private val emptyIcon: EmptyIcon = JBUIScale.scaleIcon(EmptyIcon.create(AllIcons.Actions.Checked_selected))

    @JvmStatic
    fun <T> createCommitRenderer(commitsCount: Int, presenter: (T?) -> SelectableWrapper<CommitPresentation?>): ListCellRenderer<T> {
      var commitRenderer: ListCellRenderer<T> = CommitRenderer(commitsCount, presenter)
      if (ExperimentalUI.isNewUI()) {
        commitRenderer = RoundedCellRenderer(commitRenderer, false)
      }
      return GroupedRenderer(commitRenderer, hasSeparatorBelow = { value, _ ->
        presenter(value).value == null
      })
    }
  }
}

data class CommitPresentation(
  @Language("HTML") val titleHtml: @NlsSafe String,
  @Language("HTML") val descriptionHtml: @NlsSafe String,
  val author: @NlsSafe String,
  val committedDate: Date
)