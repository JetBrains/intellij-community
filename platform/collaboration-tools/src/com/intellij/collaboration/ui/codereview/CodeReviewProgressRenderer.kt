// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.openapi.editor.colors.FontPreferences.DEFAULT_FONT_NAME
import com.intellij.openapi.editor.colors.FontPreferences.DEFAULT_FONT_SIZE
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTreeCellRenderer
import com.intellij.ui.*
import com.intellij.ui.paint.PaintUtil.RoundingMode
import com.intellij.util.ui.JBUI.Borders.emptyRight
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.FontSize
import com.intellij.util.ui.UIUtil.getFontSize
import icons.CollaborationToolsIcons.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

internal class CodeReviewProgressRenderer(
  private val renderer: ColoredTreeCellRenderer,
  private val codeReviewProgressStateProvider: (ChangesBrowserNode<*>) -> NodeCodeReviewProgressState
) : CellRendererPanel(), TreeCellRenderer {

  private val iconLabel = JLabel().apply { border = emptyRight(10) }
  private val discussionsCountFont = Font(DEFAULT_FONT_NAME, Font.PLAIN, DEFAULT_FONT_SIZE)

  init {
    buildLayout()
  }

  private fun buildLayout() {
    layout = BorderLayout()

    add(renderer, BorderLayout.CENTER)
    add(iconLabel, BorderLayout.EAST)
  }

  override fun getTreeCellRendererComponent(
    tree: JTree,
    value: Any,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean
  ): Component {
    value as ChangesBrowserNode<*>

    ChangesTreeCellRenderer.customize(this, selected)

    renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    iconLabel.icon = getIcon(value)

    return this
  }

  private fun getIcon(node: ChangesBrowserNode<*>): Icon? {
    val state = codeReviewProgressStateProvider(node)
    val isRead = state.isRead
    val discussionsCount = state.discussionsCount

    if (discussionsCount <= 0) return getReadingStateIcon(isRead)

    return getReadingStateWithDiscussionsIcon(isRead, discussionsCount)
  }

  private fun getReadingStateIcon(isRead: Boolean): Icon? = if (!isRead) FileUnread else null

  private fun getReadingStateWithDiscussionsIcon(isRead: Boolean, discussionsCount: Int): Icon {
    require(discussionsCount > 0)

    if (discussionsCount > 9) {
      return if (!isRead) CommentUnreadMany else CommentReadMany
    }

    val backgroundIcon = if (!isRead) CommentUnread else CommentUnresolved
    // use only two colors to be consistent with unread/read many icons
    val textIconColor = JBColor(Color.white, Color(0x3C3F41))
    val discussionsCountIcon = createDiscussionsCountIcon(discussionsCount, textIconColor)

    return combine(backgroundIcon, discussionsCountIcon)
  }

  private fun createDiscussionsCountIcon(discussionsCount: Int, color: Color?): Icon {
    require(discussionsCount in 1..9)
    val text: @NlsSafe String = discussionsCount.toString()
    return TextIcon(text, color, null, 0).apply {
      setFont(discussionsCountFont.deriveFont(getFontSize(FontSize.MINI)))
    }
  }

  private fun combine(backgroundIcon: Icon, textIcon: Icon): Icon {
    val horizontalTextIconShift = RoundingMode.CEIL.round((backgroundIcon.iconWidth - textIcon.iconWidth) / 2.0)
    val verticalTextIconShift = backgroundIcon.iconHeight / 8

    return LayeredIcon(2).apply {
      setIcon(backgroundIcon, 0)
      setIcon(textIcon, 1, horizontalTextIconShift, verticalTextIconShift)
    }
  }
}