// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.ui.VerticalFlowLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.OverlayLayout

class NotificationsOverlay {

  private val container = JPanel(VerticalFlowLayout(0, 0)).apply { isOpaque = false }

  fun addNotification(notification: EditorNotificationPanel) {
    container.add(notification)
    container.revalidate()
    container.repaint()
  }

  fun removeNotification(notification: EditorNotificationPanel) {
    container.remove(notification)
    container.revalidate()
    container.repaint()
  }

  fun clearNotifications() {
    container.removeAll()
    container.revalidate()
    container.repaint()
  }

  fun getComponent(): JComponent = container

  fun wrapComponent(comp: JComponent): JPanel {
    val res = object: JPanel() {
      override fun getPreferredSize(): Dimension = comp.preferredSize
      override fun getMinimumSize(): Dimension = comp.minimumSize
      override fun getMaximumSize(): Dimension = comp.maximumSize
    }
    val overlay = container
    comp.alignmentX = 0f
    comp.alignmentY = 0f
    overlay.alignmentX = 0f
    overlay.alignmentY = 0f
    res.layout = OverlayLayout(res)
    res.add(overlay)
    res.add(comp)
    return res
  }
}