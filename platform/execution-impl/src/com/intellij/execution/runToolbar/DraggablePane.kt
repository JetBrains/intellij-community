// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

@ApiStatus.Internal
open class DraggablePane : JPanel() {
  private var listener: DragListener? = null
  private var startPoint: Point? = null

  private val myUpdateQueue = MergingUpdateQueue("draggingQueue", 50, true, MergingUpdateQueue.ANY_COMPONENT)

  private val awtDragListener = AWTEventListener {
    when (it.id) {
      MouseEvent.MOUSE_RELEASED -> {
        it as MouseEvent
        getOffset(it.locationOnScreen)?.let { offset ->
          listener?.dragStopped(it.locationOnScreen, offset)
        }

        removeAWTListener()
      }

      MouseEvent.MOUSE_DRAGGED -> {
        it as MouseEvent
        getOffset(it.locationOnScreen)?.let { offset ->
          myUpdateQueue.queue(object : Update("draggingUpdate", false, 1) {
            override fun run() {
              listener?.dragged(it.locationOnScreen, offset)
            }
          })
        }
      }
    }
  }

  fun setListener(listener: DragListener) {
    this.listener = listener
  }

  private fun getOffset(locationOnScreen: Point): Dimension? {
    return startPoint?.let{
      val offsetX = locationOnScreen.x - it.x
      val offsetY = locationOnScreen.y - it.y
      Dimension(offsetX, offsetY)
    }
  }

  private val startDragListener = object : MouseAdapter() {
    override fun mouseEntered(e: MouseEvent?) {

      setCursor()
    }

    override fun mouseExited(e: MouseEvent?) {
      startPoint ?: run {
        resetCursor()
      }
    }



    override fun mousePressed(e: MouseEvent) {
      startPoint = e.locationOnScreen
      listener?.dragStarted(e.locationOnScreen)
      setCursor()
      Toolkit.getDefaultToolkit().addAWTEventListener(
        awtDragListener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
    }
  }

  private fun setCursor() {
    UIUtil.getRootPane(this)?.layeredPane?.cursor = Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)
  }

  private fun resetCursor() {
    UIUtil.getRootPane(this)?.layeredPane?.cursor = Cursor.getDefaultCursor()
  }

  override fun addNotify() {
    super.addNotify()
    addMouseListener(startDragListener)
  }

  override fun removeNotify() {
    removeListeners()
    super.removeNotify()
  }

  private fun removeAWTListener() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(awtDragListener)
    resetCursor()
    startPoint = null
  }

  private fun removeListeners() {
    removeMouseListener(startDragListener)
    removeAWTListener()
  }

  init {
    isOpaque = false
    preferredSize = JBDimension(7, 21)
    minimumSize = JBDimension(7, 21)
}

  interface DragListener {
    fun dragStarted(locationOnScreen: Point)
    fun dragged(locationOnScreen: Point, offset: Dimension)
    fun dragStopped(locationOnScreen: Point, offset: Dimension)
  }
}

internal class RunWidgetResizePane: DraggablePane() {
  private var resizeController: RunWidgetResizeController? = null

  private val listener = object : DragListener {
    override fun dragStarted(locationOnScreen: Point) {
      resizeController?.dragStarted(locationOnScreen)
    }

    override fun dragged(locationOnScreen: Point, offset: Dimension) {
      resizeController?.dragged(locationOnScreen, offset)
    }

    override fun dragStopped(locationOnScreen: Point, offset: Dimension) {
      resizeController?.dragStopped(locationOnScreen, offset)
    }
  }

  override fun addNotify() {
    super.addNotify()

    CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this))?.let {
      resizeController = RunWidgetResizeController.getInstance(it)
      setListener(listener)
    }
  }
}