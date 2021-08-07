// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.commits

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.graph.DefaultColorGenerator
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class CommitsListCellRenderer : ListCellRenderer<VcsCommitMetadata>, BorderLayoutPanel() {
  private val nodeComponent = CommitNodeComponent().apply {
    foreground = DefaultColorGenerator().getColor(1)
  }
  private val messageComponent = SimpleColoredComponent()

  init {
    addToLeft(nodeComponent)
    addToCenter(messageComponent)
  }

  override fun getListCellRendererComponent(list: JList<out VcsCommitMetadata>,
                                            value: VcsCommitMetadata?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    messageComponent.clear()
    messageComponent.append(value?.subject.orEmpty(),
                            SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getListForeground(isSelected, cellHasFocus)))
    SpeedSearchUtil.applySpeedSearchHighlighting(list, messageComponent, true, isSelected)

    val size = list.model.size
    nodeComponent.type = CommitNodeComponent.typeForListItem(index, size)

    this.background = UIUtil.getListBackground(isSelected, cellHasFocus)
    return this
  }
}