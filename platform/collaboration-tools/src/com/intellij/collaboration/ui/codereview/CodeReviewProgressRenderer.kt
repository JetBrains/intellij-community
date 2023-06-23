// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.ui.CellRendererPanel
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.util.ui.JBUI
import icons.CollaborationToolsIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.Component
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

internal class CodeReviewProgressRenderer(
  private val renderer: ColoredTreeCellRenderer,
  private val codeReviewProgressStateProvider: (ChangesBrowserNode<*>) -> NodeCodeReviewProgressState
) : CellRendererPanel(), TreeCellRenderer {

  private val iconLabel = JLabel().apply {
    border = JBUI.Borders.empty(0, TEXT_ICON_GAP, 0, ICON_BORDER)
  }

  init {
    layout = MigLayout(LC().gridGap("0", "0")
                         .insets("0", "0", "0", "0")
                         .fill()
                         .noGrid())
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

    removeAll()
    background = null
    isSelected = selected

    val comp = renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    add(comp, CC().minWidth("0").dockWest())

    val icon = getIcon(value)
    iconLabel.icon = icon
    val text = getText(value)
    iconLabel.text = text

    val fm = iconLabel.getFontMetrics(iconLabel.font)
    val width = (icon?.iconWidth ?: 0) + iconLabel.iconTextGap +
                fm.charWidth('8') * (text?.length ?: 0) +
                iconLabel.insets.left + iconLabel.insets.right
    add(iconLabel, CC().minWidth("${width}px").dockEast())

    return this
  }

  private fun getIcon(node: ChangesBrowserNode<*>): Icon? {
    val state = codeReviewProgressStateProvider(node)
    val isRead = state.isRead
    val discussionsCount = state.discussionsCount
    return if (discussionsCount <= 0) getReadingStateIcon(isRead) else getReadingStateWithDiscussionsIcon(isRead, discussionsCount)
  }

  private fun getText(node: ChangesBrowserNode<*>): @NlsSafe String? {
    val state = codeReviewProgressStateProvider(node)
    val discussionsCount = state.discussionsCount
    return if (discussionsCount <= 0) null else discussionsCount.toString()
  }

  private fun getReadingStateIcon(isRead: Boolean): Icon? = if (!isRead) CollaborationToolsIcons.FileUnread else null

  private fun getReadingStateWithDiscussionsIcon(isRead: Boolean, discussionsCount: Int): Icon {
    require(discussionsCount > 0)
    return if (!isRead) CollaborationToolsIcons.Review.CommentUnread else CollaborationToolsIcons.Review.CommentUnresolved
  }

  companion object {
    private const val TEXT_ICON_GAP = 4
    private const val ICON_BORDER = 10
  }
}