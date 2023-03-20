/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * This panel will show [emptyComponent] if current [contentComponent] is `null`.
 * That's pretty similar to `emptyView` decorator for Android's `ListView`
 */
class EmptyComponentPanel(private val emptyComponent: JComponent) {
  val component = JPanel(GridBagLayout())

  var contentComponent: JComponent? = null
    set(newComponent) {
      if (field != null) {
        component.remove(field)
      }
      if (newComponent != null) {
        component.addWithFill(newComponent)
        newComponent.isVisible = true
      }
      component.repaint()
      emptyComponent.isVisible = newComponent == null
      field = newComponent
    }

  init {
    emptyComponent.isVisible = true
    component.addWithFill(emptyComponent)
  }

  companion object {
    private fun JPanel.addWithFill(component: JComponent) {
      val constraints = GridBagConstraints().apply {
        fill = GridBagConstraints.BOTH
        weightx = 1.0
        weighty = 1.0
      }
      add(component, constraints)
    }
  }
}
