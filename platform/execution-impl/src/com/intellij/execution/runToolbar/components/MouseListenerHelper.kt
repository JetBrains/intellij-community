// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar.components

import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

class MouseListenerHelper {
  companion object {
    fun addListener(component: Component, doClick: () -> Unit, doShiftClick: () -> Unit, doRightClick: () -> Unit) {

      component.addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          if (!component.isEnabled) return

          if (SwingUtilities.isLeftMouseButton(e)) {
            e.consume()
            if (e.isShiftDown) {
              doShiftClick()
            }
            else {
              doClick()
            }
          }
          else if (SwingUtilities.isRightMouseButton(e)) {
            doRightClick()
          }
        }
      })
    }
  }
}
