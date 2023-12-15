// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.lvcs.impl.ActivityItem
import com.intellij.platform.lvcs.impl.ActivityPresentation
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

private const val ROW_LEFT_RIGHT_BORDER = 5
private const val ROW_TOP_BOTTOM_BORDER = 3
private const val ROW_LEFT_RIGHT_INSETS = 12
private const val ROW_TOP_BOTTOM_INSETS = 8
private val HIGHLIGHTED_ARC = JBUI.value(4f)
private val BACKGROUND_ARC = JBUI.value(8f)
private val HIGHLIGHT_THICKNESS = JBUI.value(4f)
private val USER_LABEL_COLOR = JBColor(Color(230, 230, 250), Color(89, 96, 74))

internal class ActivityItemRenderer(private val presentationFunction: (item: ActivityItem) -> ActivityPresentation?) : ListCellRenderer<ActivityItem> {

  private val contentPanel = BorderLayoutPanel().apply {
    isOpaque = true
    putClientProperty(JBList.IGNORE_LIST_ROW_HEIGHT, true)
    border = JBUI.Borders.empty(ROW_TOP_BOTTOM_BORDER, ROW_LEFT_RIGHT_BORDER)
  }

  override fun getListCellRendererComponent(list: JList<out ActivityItem>, value: ActivityItem, index: Int, isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    contentPanel.removeAll()
    contentPanel.background = list.background
    val activityPresentation = presentationFunction(value) ?: return contentPanel

    val cellBackgroundColor = if (isSelected) list.selectionBackground
    else if (activityPresentation.showBackground) JBUI.CurrentTheme.List.Hover.background(cellHasFocus)
    else null
    val cellHighlightColor = activityPresentation.highlightColor?.let { intToColor(it) }

    val rowComponent = createRowComponent(list, activityPresentation.text, value.timestamp, cellBackgroundColor, cellHighlightColor,
                                          isSelected)
    contentPanel.addToCenter(rowComponent)

    return contentPanel
  }

  private fun intToColor(color: Int) = color.takeIf { it != -1 }?.let { Color(it) } ?: USER_LABEL_COLOR
}

private class RoundedPanel(var roundedBackgroundColor: Color?, var highlightColor: Color?) : JBPanel<RoundedPanel>() {
  init {
    isOpaque = roundedBackgroundColor != null
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    if (roundedBackgroundColor != null || highlightColor != null) {
      val config = GraphicsUtil.setupAAPainting(g)

      val arc = if (highlightColor != null) HIGHLIGHTED_ARC.get() else BACKGROUND_ARC.get()
      if (roundedBackgroundColor != null) {
        g.color = roundedBackgroundColor
        g.fillRoundRect(0, 0, width, height, arc, arc)
      }
      if (highlightColor != null) {
        g.color = highlightColor
        g.fillRoundRect(0, 0, HIGHLIGHT_THICKNESS.get(), height, arc, arc)
      }

      config.restore()
    }
  }
}

private fun createRowComponent(list: JList<*>, @NlsContexts.Label text: String, timestamp: Long,
                               backgroundColor: Color?, highlightColor: Color?, isSelected: Boolean): RoundedPanel {
  val content = RoundedPanel(backgroundColor, highlightColor)
  content.layout = BorderLayout()
  content.font = list.font
  content.foreground = if (isSelected) list.selectionForeground else list.foreground
  content.background = null
  content.border = JBUI.Borders.empty(ROW_TOP_BOTTOM_INSETS, ROW_LEFT_RIGHT_INSETS)

  val mainLabel = JBLabel(text, SwingConstants.LEFT)
  mainLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
  content.add(mainLabel, BorderLayout.CENTER)

  if (timestamp > 0) {
    val timestampLabel = JBLabel(DateFormatUtil.formatPrettyDateTime(timestamp))
    timestampLabel.componentStyle = UIUtil.ComponentStyle.SMALL
    timestampLabel.foreground = if (isSelected) list.selectionForeground else JBColor.GRAY
    content.add(timestampLabel, BorderLayout.EAST)
  }

  return content
}