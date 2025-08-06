// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.useG2D
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
import kotlin.math.abs

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

    override fun paint(g: Graphics, c: JComponent) {
      super.paint(g, c)

      g.useG2D { g2d ->
        for ((line, color) in overlayLines) {
          g2d.color = color
          g2d.draw(line)
        }
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
      JupyterBoundsChangeHandler.get(editor).performPostponed()
    }
  }

  // Used in drawing cell frame for selected and hovered.
  fun replaceOverlayLine(oldLine: Line2D?, line: Line2D, color: Color) {
    val repaintRect = if (oldLine != null) {
      val oldBounds = Rectangle(
        oldLine.x1.toInt().coerceAtMost(oldLine.x2.toInt()),
        oldLine.y1.toInt().coerceAtMost(oldLine.y2.toInt()),
        abs(oldLine.x2.toInt() - oldLine.x1.toInt()) + 1,
        abs(oldLine.y2.toInt() - oldLine.y1.toInt()) + 1
      )

      val newBounds = Rectangle(
        line.x1.toInt().coerceAtMost(line.x2.toInt()),
        line.y1.toInt().coerceAtMost(line.y2.toInt()),
        abs(line.x2.toInt() - line.x1.toInt()) + 1,
        abs(line.y2.toInt() - line.y1.toInt()) + 1
      )

      oldBounds.union(newBounds)
    }
    else {
      Rectangle(
        line.x1.toInt().coerceAtMost(line.x2.toInt()),
        line.y1.toInt().coerceAtMost(line.y2.toInt()),
        abs(line.x2.toInt() - line.x1.toInt()) + 1,
        abs(line.y2.toInt() - line.y1.toInt()) + 1
      )
    }
    overlayLines.removeIf { it.first == oldLine }
    overlayLines.add(line to color)
    layeredPane.repaint(repaintRect)
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