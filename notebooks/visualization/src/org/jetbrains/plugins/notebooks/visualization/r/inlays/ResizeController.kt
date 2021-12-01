package org.jetbrains.plugins.notebooks.visualization.r.inlays

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.impl.FontInfo
import com.intellij.openapi.editor.impl.view.EditorPainter
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.SwingUtilities
import kotlin.math.abs

/** Realizes resize of InlayComponent by dragging resize icon in right bottom corner of component. */
class ResizeController(
  private val component: Component,
  private val editor: Editor,
  private val deltaSize: (dx: Int, dy: Int) -> Unit = component::swingDeltaSize,
) : MouseListener, MouseMotionListener {

  private var prevPoint: Point? = null

  private enum class ScaleMode { NONE, N /*, W, NW*/ }

  private var scaleMode = ScaleMode.NONE

  private val nResizeCursor = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
  private val defaultCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)

  private fun setCursor(cursor: Cursor) {
    if (component.cursor != cursor) {
      component.cursor = cursor
    }
  }

  override fun mouseReleased(e: MouseEvent?) {

    // Snapping to right margin.
    if (EditorPainter.isMarginShown(editor) && prevPoint != null) {

      val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
      val context = FontInfo.getFontRenderContext(editor.contentComponent)
      val fm = FontInfo.getFontMetrics(font, context)

      val width = FontLayoutService.getInstance().charWidth2D(fm, ' '.toInt())

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

  override fun mouseMoved(e: MouseEvent) {

    if (scaleMode != ScaleMode.NONE) {
      return
    }

    val correctedHeight = component.height - InlayDimensions.bottomBorder
    setCursor(if (e.point.y > correctedHeight) nResizeCursor else defaultCursor)
  }

  override fun mouseExited(e: MouseEvent) {
    if (scaleMode == ScaleMode.NONE) {
      setCursor(defaultCursor)
    }
  }

  override fun mouseClicked(e: MouseEvent): Unit = Unit

  override fun mouseEntered(e: MouseEvent): Unit = Unit
}

private fun Component.swingDeltaSize(dx: Int, dy: Int) {
  val oldSize = size
  size = Dimension(oldSize.width + dx, oldSize.height + dy)
  preferredSize = size
  revalidate()
  repaint()
}