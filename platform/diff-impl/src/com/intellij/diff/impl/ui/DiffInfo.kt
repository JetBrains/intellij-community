// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl.ui

import com.intellij.diff.FrameDiffTool
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

abstract class DiffInfo : BorderLayoutPanel(), FrameDiffTool.DiffInfo {
  private val leftLabel = createLabel()
  private val rightLabel = createLabel()
  private val arrows =
    JPanel(FlowLayout(FlowLayout.CENTER, 0, JBUI.scale(6)))
      .apply {
        add(JBLabel(AllIcons.Diff.ArrowLeftRight))
      }

  companion object {
    private val labelIcon = AllIcons.General.Information
  }

  override fun getComponent(): JComponent {
    return this
  }

  abstract fun getContentTitles(): List<@Nls String?>

  private fun createLabel(): JBLabel {
    val label = JBLabel("", JBLabel.CENTER)
      .withFont(JBUI.Fonts.toolbarFont())
      .withBorder(JBUI.Borders.empty(0, 6, 0, 5))
    label.iconTextGap = 0
    label.verticalTextPosition = JBLabel.CENTER
    label.horizontalTextPosition = JBLabel.LEFT
    label.setCopyable(true)
    return label
  }

  fun update() {
    val titles = getContentTitles()
    if (titles.size == 2) {
      doLayout(titles[0].orEmpty(), titles[1].orEmpty())
    }
    else {
      removeAll()
    }
  }

  private fun doLayout(left: @Nls String, right: @Nls String) {
    removeAll()
    val leftExist = left.isNotBlank()
    val rightExist = right.isNotBlank()
    val arrowsVisible = leftExist && rightExist
    setText(left, right)
    addToCenter(when {
                  arrowsVisible -> wrapCentered(vGap = 0, leftLabel, arrows, rightLabel)
                  leftExist -> wrapCentered(JBUI.scale(6), leftLabel)
                  else -> wrapCentered(JBUI.scale(6), rightLabel)
                })
  }

  private fun wrapCentered(vGap: Int, vararg components: JComponent): JComponent {
    return JPanel(FlowLayout(FlowLayout.CENTER, 0, vGap)).also { components.forEach(it::add) }
  }

  private fun setText(@NlsContexts.Label left: String, @NlsContexts.Label right: String) {
    leftLabel.text = left
    rightLabel.text = right
  }

  @Suppress("unused")
  private fun updateIconsVisibility(left: Boolean, right: Boolean) {
    leftLabel.setIconWithAlignment(if (left) labelIcon else null, JBLabel.CENTER, JBLabel.CENTER)
    rightLabel.setIconWithAlignment(if (right) labelIcon else null, JBLabel.CENTER, JBLabel.CENTER)
  }
}
