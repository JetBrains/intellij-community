// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.openapi.editor.colors.FontPreferences.DEFAULT_FONT_NAME
import com.intellij.openapi.editor.colors.FontPreferences.DEFAULT_FONT_SIZE
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTreeCellRenderer
import com.intellij.ui.CellRendererPanel
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.LayeredIcon
import com.intellij.ui.TextIcon
import com.intellij.ui.paint.PaintUtil.RoundingMode
import com.intellij.util.ui.JBUI.Borders.emptyRight
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

class CodeReviewProgressRenderer(
  private val renderer: ColoredTreeCellRenderer,
  private val readingStateProvider: (ChangesBrowserNode<*>) -> Boolean,
  private val discussionsCountProvider: (ChangesBrowserNode<*>) -> Int
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
    iconLabel.icon = getIcon(tree, value)

    return this
  }

  private fun getIcon(tree: JTree, node: ChangesBrowserNode<*>): Icon? {
    val isRead = readingStateProvider(node)
    val discussionsCount = discussionsCountProvider(node)

    if (discussionsCount <= 0) return getReadingStateIcon(isRead)

    return getReadingStateWithDiscussionsIcon(isRead, discussionsCount, foreground = tree.background)
  }

  private fun getReadingStateIcon(isRead: Boolean): Icon? = if (!isRead) FileUnread else null

  private fun getReadingStateWithDiscussionsIcon(isRead: Boolean, discussionsCount: Int, foreground: Color?): Icon {
    require(discussionsCount > 0)

    val backgroundIcon = if (!isRead) CommentUnread else CommentUnresolved

    val text: @NlsSafe String = if (discussionsCount > 9) "9+" else discussionsCount.toString()
    val textIcon = TextIcon(text, foreground, null, 0)
    textIcon.setFont(discussionsCountFont.deriveFont(getFontSize(FontSize.MINI)))

    return combine(backgroundIcon, textIcon)
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