package org.jetbrains.plugins.notebooks.visualization.r.inlays

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.impl.view.EditorPainter
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.util.EventListener
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.abs

/** Realizes height-resize of InlayComponent by dragging resize area in the bottom of the component. */
class ResizeController(
  private val component: JComponent,
  private val editor: Editor,
  private val deltaSize: (dx: Int, dy: Int) -> Unit,
) : MouseListener, MouseMotionListener {

  fun interface ResizeStateListener : EventListener {
    fun onModeChanged(newState: ResizeState)
  }

  private var prevPoint: Point? = null

  private enum class ScaleMode { NONE, N /*, W, NW*/ }

  enum class ResizeState { NONE, HOVER, RESIZING }

  private var scaleMode = ScaleMode.NONE

  private var resizeState: ResizeState = ResizeState.NONE
    set(value) {
      if (field != value) {
        field = value
        resizeStateDispatcher.multicaster.onModeChanged(value)
      }
    }

  val resizeStateDispatcher = EventDispatcher.create(ResizeStateListener::class.java)

  private fun setCursor(cursor: Cursor) {
    if (component.cursor != cursor) {
      component.cursor = cursor
    }
  }

  override fun mouseReleased(e: MouseEvent) {
    // Snapping to right margin.
    if (EditorPainter.isMarginShown(editor) && prevPoint != null) {

      val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
      val context = FontInfo.getFontRenderContext(editor.contentComponent)
      val fm = FontInfo.getFontMetrics(font, context)

      val width = FontLayoutService.getInstance().charWidth2D(fm, ' '.code)

      val rightMargin = editor.settings.getRightMargin(editor.project) * width

      SwingUtilities.convertPointFromScreen(prevPoint!!, editor.contentComponent)

      if (abs(prevPoint!!.x - rightMargin) < JBUI.scale(40)) {
        deltaSize(rightMargin.toInt() - prevPoint!!.x, 0)
        SwingUtilities.invokeLater {
          component.revalidate()
          component.repaint()
        }
      }
    }

    prevPoint = null
    scaleMode = ScaleMode.NONE
    resizeState = if (mouseInResizeArea(e)) ResizeState.HOVER else ResizeState.NONE
  }

  override fun mousePressed(e: MouseEvent) {
    val correctedHeight = component.height - InlayDimensions.bottomBorder

    scaleMode = if (e.point.y > correctedHeight) {
      ScaleMode.N
    }
    else {
      return
    }

    prevPoint = e.locationOnScreen
    resizeState = ResizeState.RESIZING
  }

  override fun mouseDragged(e: MouseEvent?) {
    if (prevPoint == null) {
      return
    }

    val locationOnScreen = e!!.locationOnScreen

    val dy = if (scaleMode == ScaleMode.N) locationOnScreen.y - prevPoint!!.y else 0

    deltaSize(0, dy)
    prevPoint = locationOnScreen
  }

  private fun mouseInResizeArea(e: MouseEvent) = e.point.y > component.height - component.insets.bottom

  override fun mouseMoved(e: MouseEvent) {
    if (scaleMode != ScaleMode.NONE) {
      return
    }

    val canResize = mouseInResizeArea(e)
    setCursor(if (canResize) Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR) else Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
    resizeState = if (canResize) ResizeState.HOVER else ResizeState.NONE
  }

  override fun mouseExited(e: MouseEvent) {
    if (scaleMode == ScaleMode.NONE) {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
      resizeState = ResizeState.NONE
    }
  }

  override fun mouseClicked(e: MouseEvent): Unit = Unit

  override fun mouseEntered(e: MouseEvent): Unit = Unit
}