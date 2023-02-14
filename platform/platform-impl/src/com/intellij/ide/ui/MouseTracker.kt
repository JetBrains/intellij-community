// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

@ApiStatus.Internal
class MouseTracker(component: JComponent) {

  fun interface MoveListener {
    fun changed(oldMousePoint: Point?, newMousePoint: Point?)
  }

  private val moveListeners = mutableListOf<MoveListener>()

  var mousePoint: Point? = null
    private set

  private val mouseListener = object : MouseAdapter() {
    override fun mouseEntered(e: MouseEvent?) {
      updateMousePoint(e?.point)
    }

    override fun mouseExited(e: MouseEvent?) {
      updateMousePoint(null)
    }

    override fun mouseMoved(e: MouseEvent?) {
      updateMousePoint(e?.point)
    }
  }

  init {
    component.addMouseListener(mouseListener)
    component.addMouseMotionListener(mouseListener)
  }

  fun addMoveListener(listener: MoveListener) {
    moveListeners += listener
  }

  private fun updateMousePoint(point: Point?) {
    if (point != mousePoint) {
      val oldPoint = mousePoint
      mousePoint = point
      for (listener in moveListeners) {
        listener.changed(oldPoint, mousePoint)
      }
    }
  }
}
