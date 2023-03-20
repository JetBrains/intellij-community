// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.util.ui.SingleComponentCenteringLayout
import java.awt.*
import javax.swing.JComponent
import javax.swing.JLayeredPane

object JComponentOverlay {

  fun createCentered(component: JComponent, centeredOverlay: JComponent): JLayeredPane {
    val pane = object : JLayeredPane() {
      override fun getPreferredSize(): Dimension = component.preferredSize

      override fun getMinimumSize(): Dimension = component.minimumSize

      override fun getMaximumSize(): Dimension = component.maximumSize

      override fun isVisible(): Boolean = component.isVisible

      override fun doLayout() {
        super.doLayout()
        component.setBounds(0, 0, width, height)
        centeredOverlay.bounds = SingleComponentCenteringLayout.getBoundsForCentered(component, centeredOverlay)
      }
    }
    pane.isFocusable = false
    pane.add(component, JLayeredPane.DEFAULT_LAYER, -1)
    pane.add(centeredOverlay, JLayeredPane.PALETTE_LAYER, -1)
    return pane
  }
}