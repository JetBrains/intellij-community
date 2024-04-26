// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.create

import com.intellij.collaboration.ui.codereview.commits.CommitNodeComponent
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsCommitMetadata
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Rectangle
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * use [CodeReviewCreateReviewUIUtil.createCommitListCellRenderer] instead
 */
@ApiStatus.Internal
class CodeReviewTwoLinesCommitRenderer : ListCellRenderer<VcsCommitMetadata>, BorderLayoutPanel() {
  private val nodeComponent: MyCommitNodeComponent = MyCommitNodeComponent().apply {
    foreground = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
  }

  private val commitRenderer = TwoLinesCommitRenderer<VcsCommitMetadata>(
    { it.subject },
    { "${it.author.name} ${DateFormatUtil.formatPrettyDateTime(it.commitTime)}" }
  ).apply {
    border = JBUI.Borders.emptyLeft(BASE_GAP)
  }

  init {
    border = JBUI.Borders.emptyLeft(BASE_GAP)
    addToLeft(nodeComponent)
    addToCenter(commitRenderer)
  }

  override fun getListCellRendererComponent(list: JList<out VcsCommitMetadata>,
                                            value: VcsCommitMetadata,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    nodeComponent.apply {
      foreground = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
    }

    commitRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

    val size = list.model.size
    nodeComponent.type = CommitNodeComponent.typeForListItem(index, size)

    UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus()))
    return this
  }

  companion object {
    private const val BASE_GAP = 12
  }
}

private class MyCommitNodeComponent : CommitNodeComponent() {

  override fun getPreferredSize(): JBDimension = JBDimension(NODE_SIZE, NODE_SIZE)

  override fun calcRadius(rect: Rectangle): Int = JBUI.scale(NODE_SIZE / 2) - 1

  override fun calcLineThickness(rect: Rectangle): Float = JBUIScale.scale(1.5f)

  companion object {
    private const val NODE_SIZE = 10
  }
}

@ApiStatus.Internal
class TwoLinesCommitRenderer<T>(
  private val getCommitMessage: (T) -> @Nls String,
  private val getAuthorAndDateLine: (T) -> @Nls String
) : ListCellRenderer<T>, BorderLayoutPanel() {


  private val commitMessage: SimpleColoredComponent = SimpleColoredComponent().apply {
    isOpaque = false
    border = JBUI.Borders.emptyTop(TOP_BOTTOM_OFFSET)
  }

  private val authorAndDate: SimpleColoredComponent = SimpleColoredComponent().apply {
    isOpaque = false
    border = JBUI.Borders.emptyBottom(TOP_BOTTOM_OFFSET)
  }


  init {
    addToCenter(commitMessage).addToBottom(authorAndDate)
  }

  override fun getListCellRendererComponent(list: JList<out T>,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    commitMessage.apply {
      clear()
      append(getCommitMessage(value))
      foreground = ListUiUtil.WithTallRow.foreground(isSelected, list.hasFocus())
      SpeedSearchUtil.applySpeedSearchHighlighting(list, this, true, isSelected)
    }

    authorAndDate.apply {
      clear()
      append(getAuthorAndDateLine(value))
      foreground = ListUiUtil.WithTallRow.secondaryForeground(isSelected, list.hasFocus())
    }


    UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected, list.hasFocus()))
    return this
  }

  companion object {
    private const val TOP_BOTTOM_OFFSET = 4
  }
}
