// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview

import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent

object ToggleableContainer {
  fun create(
    model: SingleValueModel<Boolean>,
    mainComponentSupplier: () -> JComponent,
    toggleableComponentSupplier: () -> JComponent
  ): JComponent {

    val container = BorderLayoutPanel().apply {
      isOpaque = false
      addToCenter(mainComponentSupplier())
    }
    model.addValueUpdatedListener { newValue ->
      if (newValue) {
        updateToggleableContainer(container, toggleableComponentSupplier())
      }
      else {
        updateToggleableContainer(container, mainComponentSupplier())
      }
    }
    return container
  }

  private fun updateToggleableContainer(container: BorderLayoutPanel, component: JComponent) {
    with(container) {
      removeAll()
      addToCenter(component)
      revalidate()
      repaint()

      val focusManager = IdeFocusManager.findInstanceByComponent(component)
      val toFocus = focusManager.getFocusTargetFor(component) ?: return
      focusManager.doWhenFocusSettlesDown { focusManager.requestFocus(toFocus, true) }
    }
  }
}