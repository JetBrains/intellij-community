// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.ui.CellRendererPanel
import com.intellij.ui.ClientProperty
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.CollaborationToolsIcons
import java.awt.*
import javax.swing.*
import javax.swing.tree.TreeCellRenderer

internal class CodeReviewProgressRenderer(
  hasViewedState: Boolean,
  renderer: ColoredTreeCellRenderer,
  codeReviewProgressStateProvider: (ChangesBrowserNode<*>) -> NodeCodeReviewProgressState,
) : TreeCellRenderer {
  private val component = CodeReviewProgressRendererComponent(hasViewedState, renderer, codeReviewProgressStateProvider)

  override fun getTreeCellRendererComponent(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component =
    component.prepareComponent(tree, value, selected, expanded, leaf, row, hasFocus)
}

internal class CodeReviewProgressRendererComponent(
  private val hasViewedState: Boolean,
  private val renderer: ColoredTreeCellRenderer,
  private val codeReviewProgressStateProvider: (ChangesBrowserNode<*>) -> NodeCodeReviewProgressState,
) : CellRendererPanel() {
  private val checkbox = JCheckBox().apply {
    isOpaque = false
    border = JBUI.Borders.empty()
  }

  private val commentIconLabel = JLabel().apply {
    border = JBUI.Borders.empty()
    iconTextGap = JBUI.scale(TEXT_ICON_GAP)
    icon = CollaborationToolsIcons.Review.CommentUnread
  }

  private val unreadIconLabel = JLabel().apply {
    border = JBUI.Borders.empty()
    icon = CollaborationToolsIcons.Review.FileUnread
    horizontalAlignment = JLabel.CENTER
    verticalAlignment = JLabel.CENTER
  }

  private val invisiblePlaceholder = JLabel().apply {
    isVisible = false
  }

  private val unreadOrCheckboxContainer = JPanel().apply {
    isOpaque = false
    border = JBUI.Borders.empty()

    val sizeRestriction = object : DimensionRestrictions {
      override fun getWidth(): Int = checkbox.preferredSize.width
      override fun getHeight(): Int? = null
    }
    layout = SizeRestrictedSingleComponentLayout().apply {
      prefSize = sizeRestriction
      minSize = sizeRestriction
      maxSize = sizeRestriction
    }
  }

  private val stateContainer = HorizontalListPanel(gap = COMMENT_AND_UNREAD_GAP)

  init {
    layout = BorderLayout()

    ClientProperty.put(this, ExpandableItemsHandler.RENDERER_DISABLED, true)
  }

  @RequiresEdt
  fun checkboxBounds(cellSize: Dimension): Rectangle? {
    bounds = Rectangle(0, 0, cellSize.width, cellSize.height)
    return checkbox.calculateBoundsWithin(this)
  }

  @RequiresEdt
  fun prepareComponent(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): JComponent {
    value as ChangesBrowserNode<*>
    val state = codeReviewProgressStateProvider(value)

    border = JBUI.Borders.empty(0, LEFT_SIDE_GAP, 0, RIGHT_SIDE_GAP)
    background = null
    isSelected = selected

    removeAll()
    add(updateFilenameContainer(tree, value, selected, expanded, leaf, row, hasFocus), BorderLayout.CENTER)

    // if loading, don't show any icons yet
    if (isStateContainerShown(leaf, expanded) && !state.isLoading) {
      add(updateStateContainer(tree, state, row, leaf), BorderLayout.EAST)
    }

    return this
  }

  private fun updateFilenameContainer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component =
    renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus).apply {
      background = null
      minimumSize = Dimension()
      (this as? JComponent)?.border = JBUI.Borders.empty()
    }

  private fun updateStateContainer(tree: JTree, state: NodeCodeReviewProgressState, row: Int, isLeaf: Boolean): JComponent =
    stateContainer.apply {
      removeAll()

      val commentIconLabel = updateCommentIconLabel(state)
      commentIconLabel?.let(::add)

      val isHovered = TreeHoverListener.getHoveredRow(tree) == row
      val rightSideComp = if (isLeaf && hasViewedState && (isHovered || state.isRead)) {
        updateViewedCheckbox(state)
      }
      else if (!state.isRead) {
        unreadIconLabel
      }
      else null

      if (commentIconLabel != null || rightSideComp != null) {
        add(unreadOrCheckboxContainer.apply {
          removeAll()
          add(rightSideComp ?: invisiblePlaceholder)
        })
      }
    }

  private fun isStateContainerShown(isLeaf: Boolean, isExpanded: Boolean): Boolean =
    isLeaf || !isExpanded

  private fun updateViewedCheckbox(valueData: NodeCodeReviewProgressState): JComponent {
    checkbox.isSelected = valueData.isRead
    return checkbox
  }

  private fun updateCommentIconLabel(state: NodeCodeReviewProgressState): JComponent? {
    if (state.discussionsCount <= 0) return null

    commentIconLabel.icon = if (state.isRead) CollaborationToolsIcons.Review.CommentUnresolved else CollaborationToolsIcons.Review.CommentUnread
    commentIconLabel.text = state.discussionsCount.toString()

    return commentIconLabel
  }

  companion object {
    private const val TEXT_ICON_GAP = 4
    private const val COMMENT_AND_UNREAD_GAP = 10

    private const val LEFT_SIDE_GAP = 4
    private const val RIGHT_SIDE_GAP = 16
  }
}

private fun JComponent.calculateBoundsWithin(parent: JComponent): Rectangle? {
  if (!SwingUtilities.isDescendingFrom(this, parent)) return null

  // Perform layouts on all parents in top-down order
  UIUtil.layoutRecursively(parent)

  // Get and translate bounds to the parent
  val bounds = Rectangle(bounds)
  val translation = SwingUtilities.convertPoint(this, Point(0, 0), parent)
  bounds.translate(translation.x, translation.y)

  return bounds
}
