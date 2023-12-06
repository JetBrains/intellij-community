// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl.ui

import com.intellij.diff.FrameDiffTool
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.FontUtil
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

abstract class DiffInfo : FrameDiffTool.DiffInfo {
  private val leftLabel = createLabel()
  private val rightLabel = createLabel()
  private val arrows = JBLabel(AllIcons.Diff.ArrowLeftRight).apply {
    isVisible = false
  }

  private val panel: DialogPanel = panel {
    row {
      cell(leftLabel).align(AlignY.CENTER)
      cell(arrows).align(AlignY.CENTER)
      cell(rightLabel).align(AlignY.CENTER)
    }
  }.andTransparent()

  override fun getComponent(): JComponent = panel

  abstract fun getContentTitles(): List<@Nls String?>

  private fun createLabel(): JBLabel {
    val label = JBLabel(FontUtil.spaceAndThinSpace(), JBLabel.CENTER)
    label.setCopyable(true)
    label.isVisible = false
    return label
  }

  fun update() {
    val titles = getContentTitles()
    doLayout(titles.getOrNull(0).orEmpty(), titles.getOrNull(1).orEmpty())
  }

  private fun doLayout(left: @Nls String, right: @Nls String) {
    setText(left, right)

    rightLabel.isVisible = right.isNotBlank()
    leftLabel.isVisible = left.isNotBlank()
    arrows.isVisible = left.isNotBlank() && right.isNotBlank()

    panel.validate()
    panel.repaint()
  }

  private fun setText(@NlsContexts.Label left: String, @NlsContexts.Label right: String) {
    leftLabel.text = left
    rightLabel.text = right
  }
}
