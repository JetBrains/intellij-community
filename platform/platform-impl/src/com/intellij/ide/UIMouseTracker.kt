// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import java.awt.*
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import kotlin.math.sqrt

/**
 * @author Konstantin Bulenkov
 */
class UIMouseTracker : IdeEventQueue.EventDispatcher {
  var mouseCoordinates = MouseInfo.getPointerInfo().location ?: Point(0,0)
  var totalMouseTrack = 0f

  override fun dispatch(e: AWTEvent): Boolean {
    if (e is MouseEvent) {
      when (e.id) {
        MouseEvent.MOUSE_RELEASED -> onMouseClicked(e)
        MouseEvent.MOUSE_MOVED -> onMouseMove(e)
      }
    }

    return false
  }

  private fun onMouseMove(e: MouseEvent) {
    val dX = (e.xOnScreen - mouseCoordinates.x).toFloat()
    val dY = (e.yOnScreen - mouseCoordinates.y).toFloat()
    mouseCoordinates.x = e.xOnScreen
    mouseCoordinates.y = e.yOnScreen
    totalMouseTrack += sqrt(dX*dX + dY*dY)
  }

  private fun onMouseClicked(e: MouseEvent) {
    if (e.clickCount == 0) return

    val component = e.component
    val clickedComponent = if (component is Container) component.findComponentAt(e.point) else null

    if (clickedComponent != null) {
      if (clickedComponent is IdeGlassPaneImpl) {
        val dialog = DialogWrapper.findInstance(clickedComponent)
        if (dialog != null) {
          val pointOnDialog = SwingUtilities.convertPoint(e.component, e.point, dialog.contentPane)
          val comp = dialog.contentPane.findComponentAt(pointOnDialog.x, pointOnDialog.y)
          if (comp != null) {
            componentClicked(comp)
          }
        }
      } else {
        componentClicked(clickedComponent)
      }
    }
  }

  private fun componentClicked(comp: Component) {
    //todo[kb] API with UI components to report stats
  }
}