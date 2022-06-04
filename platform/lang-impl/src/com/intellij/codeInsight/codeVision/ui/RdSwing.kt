@file:Suppress("DuplicatedCode")

package com.intellij.codeInsight.codeVision.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollingModel
import com.intellij.openapi.editor.event.*
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.rd.swing.proxyProperty
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.switchMap
import java.awt.*
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.AncestorEvent

fun JComponent.windowAncestor(): IPropertyView<Window?> = proxyProperty(
  SwingUtilities.getWindowAncestor(this@windowAncestor)
) { lifetime, set ->
  val listener = object : AncestorListenerAdapter() {
    override fun ancestorAdded(event: AncestorEvent) {
      set(SwingUtilities.getWindowAncestor(this@windowAncestor))
    }

    override fun ancestorRemoved(event: AncestorEvent) {
      set(null)
    }
  }

  lifetime.bracket(
    { this@windowAncestor.addAncestorListener(listener) },
    { this@windowAncestor.removeAncestorListener(listener) }
  )
}


fun Editor.mousePressed(): ISource<EditorMouseEvent> {
  return object : ISource<EditorMouseEvent> {
    override fun advise(lifetime: Lifetime, handler: (EditorMouseEvent) -> Unit) {
      val clickListener = object : EditorMouseListener {
        override fun mousePressed(event: EditorMouseEvent) {
          handler(event)
        }
      }

      this@mousePressed.addEditorMouseListener(clickListener)
      lifetime.onTermination {
        this@mousePressed.removeEditorMouseListener(clickListener)
      }
    }

  }
}

fun Editor.mouseReleased(): ISource<EditorMouseEvent> {
  return object : ISource<EditorMouseEvent> {
    override fun advise(lifetime: Lifetime, handler: (EditorMouseEvent) -> Unit) {
      val clickListener = object : EditorMouseListener {
        override fun mouseReleased(event: EditorMouseEvent) {
          handler(event)
        }
      }

      this@mouseReleased.addEditorMouseListener(clickListener)
      lifetime.onTermination {
        this@mouseReleased.removeEditorMouseListener(clickListener)
      }
    }
  }
}

fun Editor.editorMouseListener(): ISource<EditorMouseEvent> = object : ISource<EditorMouseEvent> {
  override fun advise(lifetime: Lifetime, handler: (EditorMouseEvent) -> Unit) {

    val listener = object : EditorMouseMotionListener {
      override fun mouseMoved(e: EditorMouseEvent) {
        handler(e)
      }
    }

    this@editorMouseListener.addEditorMouseMotionListener(listener)
    lifetime.onTermination {
      this@editorMouseListener.removeEditorMouseMotionListener(listener)
    }

  }
}

fun Editor.mouseRelativePoint(): IPropertyView<Point?> {
  return proxyProperty(this@mouseRelativePoint.contentComponent.getMousePositionSafe(true)) { lifetime, set ->
    this@mouseRelativePoint.editorMouseListener().advise(lifetime) {
      val relativePoint = RelativePoint(it.mouseEvent)
      set(
        if (it.area == EditorMouseEventArea.EDITING_AREA && component.contains(relativePoint.getPoint(component))
        ) {
          it.mouseEvent.point
        }
        else null
      )
    }

    this@mouseRelativePoint.scrollingModel.visibleAreaChanged().advise(lifetime) {
      val position = this@mouseRelativePoint.contentComponent.getMousePositionSafe(true)
      set(position)
    }
  }
}


fun Editor.mousePoint(): IPropertyView<Point?> = mouseEntered().switchMap {
  if (it) {
    this@mousePoint.mouseRelativePoint()
  }
  else {
    Property(null)
  }
}

fun ScrollingModel.visibleAreaChanged(): ISource<VisibleAreaEvent> = object : ISource<VisibleAreaEvent> {
  override fun advise(lifetime: Lifetime, handler: (VisibleAreaEvent) -> Unit) {
    val visibleAreaListener = VisibleAreaListener {
      handler(it)
    }
    this@visibleAreaChanged.addVisibleAreaListener(visibleAreaListener)
    lifetime.onTermination {
      this@visibleAreaChanged.removeVisibleAreaListener(visibleAreaListener)
    }
  }
}

fun Editor.mouseEntered(): IPropertyView<Boolean> {
  return proxyProperty(this@mouseEntered.component.getMousePositionSafe() != null) { lifetime, set ->
    val listener = object : EditorMouseListener {
      override fun mouseEntered(event: EditorMouseEvent) {
        set(true)
      }

      override fun mouseExited(event: EditorMouseEvent) {
        set(false)
      }
    }

    lifetime.bracket({
                       this@mouseEntered.addEditorMouseListener(listener)
                     },
                     {
                       this@mouseEntered.removeEditorMouseListener(listener)
                     })
  }
}

/**
 * NOTE: [Container.getMousePosition] may throw NullPointerException if the cursor is outside of all detected screens. See JBR-2711 for
 * details.
 */
private fun Container.getMousePositionSafe(allowChildren: Boolean): Point? {
  return if (MouseInfo.getPointerInfo() == null) null else getMousePosition(allowChildren)
}

/**
 * NOTE: [Component.getMousePosition] may throw NullPointerException if the cursor is outside of all detected screens. See JBR-2711 for
 * details.
 */
private fun Component.getMousePositionSafe(): Point? {
  return if (MouseInfo.getPointerInfo() == null) null else mousePosition
}