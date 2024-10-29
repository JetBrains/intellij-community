// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar.components

import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ContainerAdapter
import java.awt.event.ContainerEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

@ApiStatus.Internal
class MouseListenerHelper {
  companion object {
    fun addListener(component: JComponent, doClick: () -> Unit, doShiftClick: () -> Unit, doRightClick: () -> Unit) {
      val listener = object : MouseAdapter() {
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
      }

      component.addMouseListener(listener)
      UIUtil.uiTraverser(component).traverse().forEach { comp -> comp.addMouseListener(listener) }

      component.addContainerListener(object : ContainerAdapter() {
        override fun componentAdded(e: ContainerEvent?) {
          e?.child?.addMouseListener(listener)
        }

        override fun componentRemoved(e: ContainerEvent?) {
          e?.child?.removeMouseListener(listener)
        }
      })
    }
  }
}

