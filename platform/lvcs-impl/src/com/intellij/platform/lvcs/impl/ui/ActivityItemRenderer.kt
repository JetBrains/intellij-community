// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.lvcs.impl.ActivityItem
import com.intellij.platform.lvcs.impl.ActivityPresentation
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.hover.ListHoverListener
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Color
import java.awt.Component
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

private const val ROW_LEFT_RIGHT_BORDER = 5
private const val ROW_LEFT_RIGHT_INSETS = 10
private const val ROW_TOP_BOTTOM_INSETS = 6
private val BACKGROUND_ARC = JBUI.value(8f)
private val VGAP = JBUI.value(2f)

internal class ActivityItemRenderer(private val presentationFunction: (item: ActivityItem) -> ActivityPresentation?) : ListCellRenderer<ActivityItem> {

  private val contentPanel = object : BorderLayoutPanel() {
    override fun getAccessibleContext(): AccessibleContext {
      if (accessibleContext == null) {
        accessibleContext = object : AccessibleJPanel() {
          override fun getAccessibleRole() = AccessibleRole.LIST_ITEM
        }
      }
      return accessibleContext
    }
  }.apply {
    isOpaque = true
    putClientProperty(JBList.IGNORE_LIST_ROW_HEIGHT, true)
    border = JBUI.Borders.empty(0, ROW_LEFT_RIGHT_BORDER)
  }

  override fun getListCellRendererComponent(list: JList<out ActivityItem>, value: ActivityItem, index: Int, isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    contentPanel.removeAll()
    contentPanel.background = list.background
    val activityPresentation = presentationFunction(value) ?: return contentPanel

    val isSelectionTop = isSelected && (index == 0 || !list.isSelectedIndex(index - 1))
    val isSelectionBottom = isSelected && (index == list.model.size - 1 || !list.isSelectedIndex(index + 1))

    val cellBackgroundColor = if (isSelected) list.selectionBackground
    else if (index == ListHoverListener.getHoveredIndex(list)) JBUI.CurrentTheme.List.Hover.background(cellHasFocus)
    else if (activityPresentation.color != -1) {
      @Suppress("UseJBColor")
      JBColor(activityPresentation.color, Color(activityPresentation.color).darker().rgb)
    }
    else null

    val rowComponent = createRowComponent(list, activityPresentation.text, activityPresentation.icon, value.timestamp,
                                          cellBackgroundColor, isSelected, isSelectionTop, isSelectionBottom)
    contentPanel.addToCenter(rowComponent)
    contentPanel.accessibleContext.accessibleName = rowComponent.accessibleContext.accessibleName

    return contentPanel
  }
}

private fun createRowComponent(list: JList<*>, @NlsContexts.Label text: String, icon: Icon?, timestamp: Long,
                               backgroundColor: Color?, isSelected: Boolean, isSelectionTop: Boolean, isSelectionBottom: Boolean): SelectablePanel {
  val content = SelectablePanel()
  content.selectionColor = backgroundColor
  content.selectionArc = if (backgroundColor != null) BACKGROUND_ARC.get() else 0
  content.selectionArcCorners = if (isSelected) getSelectionArcCorners(isSelectionTop, isSelectionBottom)
  else if (backgroundColor != null) SelectablePanel.SelectionArcCorners.ALL else SelectablePanel.SelectionArcCorners.NONE
  content.layout = VerticalLayout(VGAP.get(), SwingConstants.LEFT)
  content.font = list.font
  content.foreground = if (isSelected) list.selectionForeground else list.foreground
  content.background = null
  content.border = JBUI.Borders.empty(ROW_TOP_BOTTOM_INSETS, ROW_LEFT_RIGHT_INSETS)

  val mainLabel = JBLabel(text, icon, SwingConstants.LEFT)
  mainLabel.foreground = if (isSelected) list.selectionForeground else list.foreground
  content.add(mainLabel)

  if (timestamp > 0) {
    val timestampLabel = JBLabel(DateFormatUtil.formatDateTime(timestamp))
    timestampLabel.componentStyle = UIUtil.ComponentStyle.SMALL
    timestampLabel.foreground = if (isSelected) list.selectionForeground else JBColor.GRAY
    content.add(timestampLabel)
    content.accessibleContext.accessibleName = AccessibleContextUtil.getCombinedName("\n", mainLabel, timestampLabel)
  } else {
    content.accessibleContext.accessibleName = mainLabel.accessibleContext.accessibleName
  }

  return content
}

private fun getSelectionArcCorners(isSelectionTop: Boolean, isSelectionBottom: Boolean): SelectablePanel.SelectionArcCorners {
  return if (isSelectionTop && isSelectionBottom) SelectablePanel.SelectionArcCorners.ALL
  else if (isSelectionTop) SelectablePanel.SelectionArcCorners.TOP
  else if (isSelectionBottom) SelectablePanel.SelectionArcCorners.BOTTOM
  else SelectablePanel.SelectionArcCorners.NONE
}
