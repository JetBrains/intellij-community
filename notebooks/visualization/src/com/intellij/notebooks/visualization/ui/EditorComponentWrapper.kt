// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key
import java.awt.*
import java.awt.event.*
import java.awt.geom.Line2D
import javax.swing.JComponent
import javax.swing.JLayer
import javax.swing.JPanel
import javax.swing.JViewport
import javax.swing.plaf.LayerUI

/**
 * Performs updating of underlying components within keepScrollingPositionWhile.
 * Transfers mouse move-click-wheel events to the listeners.
 */
class EditorComponentWrapper private constructor(private val editor: EditorImpl) : JPanel(BorderLayout()) {

  private val overlayLines = mutableListOf<Pair<Line2D, Color>>()

  // JLayer here is our frame borders around notebook cells + to transfer ALL mouse events over editor, to subscriber.
  private val layeredPane: JLayer<Component>

  private var editorMouseListener: MouseListener? = null
  private var editorMouseMotionListener: MouseMotionListener? = null
  private var editorMouseWheelListener: MouseWheelListener? = null

  init {
    isOpaque = false

    val editorPanel = JPanel(BorderLayout()).apply {
      isOpaque = false
      val viewportWrapper = object : JViewport() {
        override fun getViewRect() = editor.scrollPane.viewport.viewRect
      }
      viewportWrapper.view = editor.contentComponent
      add(viewportWrapper, BorderLayout.CENTER)
    }

    layeredPane = JLayer(editorPanel, EditorComponentWrapperLayerUI())

    add(layeredPane, BorderLayout.CENTER)

    setupGutterWrapper()
  }

  private fun setupGutterWrapper() {
    editor.scrollPane.rowHeader.view = JLayer(editor.scrollPane.rowHeader.view, EditorComponentWrapperLayerUI())
  }

  inner class EditorComponentWrapperLayerUI : LayerUI<Component>() {
    override fun installUI(c: JComponent) {
      super.installUI(c)
      val layer = c as JLayer<*>
      layer.setLayerEventMask(AWTEvent.MOUSE_EVENT_MASK or
                                AWTEvent.MOUSE_MOTION_EVENT_MASK or
                                AWTEvent.MOUSE_WHEEL_EVENT_MASK)
    }

    override fun uninstallUI(c: JComponent) {
      super.uninstallUI(c)
      val layer = c as JLayer<*>
      layer.setLayerEventMask(0)
    }

    override fun paint(graphics: Graphics, component: JComponent) {
      super.paint(graphics, component)

      val g2d = graphics.create() as Graphics2D
      try {
        for ((line, color) in overlayLines) {
          g2d.color = color
          g2d.draw(line)
        }
      }
      finally {
        g2d.dispose()
      }
    }

    override fun processMouseEvent(e: MouseEvent, layer: JLayer<out Component>) {
      when (e.id) {
        MouseEvent.MOUSE_PRESSED -> editorMouseListener?.mousePressed(e)
        MouseEvent.MOUSE_RELEASED -> editorMouseListener?.mouseReleased(e)
        MouseEvent.MOUSE_CLICKED -> editorMouseListener?.mouseClicked(e)
        MouseEvent.MOUSE_ENTERED -> editorMouseListener?.mouseEntered(e)
        MouseEvent.MOUSE_EXITED -> editorMouseListener?.mouseExited(e)
      }
    }

    override fun processMouseMotionEvent(e: MouseEvent, layer: JLayer<out Component>) {
      when (e.id) {
        MouseEvent.MOUSE_MOVED -> editorMouseMotionListener?.mouseMoved(e)
        MouseEvent.MOUSE_DRAGGED -> editorMouseMotionListener?.mouseDragged(e)
      }
    }

    override fun processMouseWheelEvent(e: MouseWheelEvent, layer: JLayer<out Component>) {
      editorMouseWheelListener?.mouseWheelMoved(e)
    }
  }

  fun addEditorMouseEventListener(l: MouseListener) {
    editorMouseListener = AWTEventMulticaster.add(editorMouseListener, l)
  }

  fun removeEditorMouseEventListener(l: MouseListener) {
    editorMouseListener = AWTEventMulticaster.remove(editorMouseListener, l)
  }

  fun addEditorMouseMotionEvent(l: MouseMotionListener) {
    editorMouseMotionListener = AWTEventMulticaster.add(editorMouseMotionListener, l)
  }

  fun removeEditorMouseMotionEvent(l: MouseMotionListener) {
    editorMouseMotionListener = AWTEventMulticaster.remove(editorMouseMotionListener, l)
  }

  fun addEditorMouseWheelEvent(l: MouseWheelListener) {
    editorMouseWheelListener = AWTEventMulticaster.add(editorMouseWheelListener, l)
  }

  override fun validateTree() {
    editor.notebookEditor.editorPositionKeeper.keepScrollingPositionWhile {
      JupyterBoundsChangeHandler.get(editor).postponeUpdates()
      super.validateTree()
      JupyterBoundsChangeHandler.get(editor).schedulePerformPostponed()
    }
  }

  // Used in drawing cell frame for selected and hovered .
  fun addOverlayLine(line: Line2D, color: Color) {
    overlayLines.add(line to color)
    layeredPane.repaint()
  }

  fun removeOverlayLine(line: Line2D) {
    overlayLines.removeIf { it.first == line }
    layeredPane.repaint()
  }

  companion object {
    private val EDITOR_COMPONENT_WRAPPER = Key<EditorComponentWrapper>("EDITOR_COMPONENT_WRAPPER")

    fun install(editor: EditorImpl): EditorComponentWrapper {
      val wrapper = EditorComponentWrapper(editor)
      editor.scrollPane.viewport.view = wrapper
      editor.putUserData(EDITOR_COMPONENT_WRAPPER, wrapper)
      return wrapper
    }

    fun get(editor: Editor): EditorComponentWrapper = editor.getUserData(EDITOR_COMPONENT_WRAPPER)!!
  }
}