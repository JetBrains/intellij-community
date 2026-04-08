// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.interaction

import com.intellij.codeInsight.hints.presentation.InputHandler
import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.MinimapUsageCollector
import com.intellij.ide.minimap.hover.MinimapHoverController
import com.intellij.openapi.Disposable
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.max

class MinimapMouseInteractionController(
  private val panel: MinimapPanel,
  private val hoverController: MinimapHoverController
) : MouseAdapter(), Disposable {
  private enum class MinimapMouseInteractionState { IDLE, DRAGGING }

  private val editor = panel.editor
  private var interactionState: MinimapMouseInteractionState = MinimapMouseInteractionState.IDLE
  private var dragAnimationDisabled = false
  private var dragOffset = 0
  private var dragStartY = 0
  private var dragDistancePx = 0

  fun install() {
    panel.addMouseListener(this)
    panel.addMouseWheelListener(this)
    panel.addMouseMotionListener(this)
  }

  override fun dispose() {
    if (dragAnimationDisabled) {
      editor.scrollingModel.enableAnimation()
      dragAnimationDisabled = false
    }

    interactionState = MinimapMouseInteractionState.IDLE
    dragOffset = 0
    dragStartY = 0
    dragDistancePx = 0

    panel.removeMouseListener(this)
    panel.removeMouseWheelListener(this)
    panel.removeMouseMotionListener(this)
  }

  override fun mousePressed(e: MouseEvent) {
    if (e.button != MouseEvent.BUTTON1) return

    val geometry = panel.currentSnapshot()?.geometry
    if (geometry == null || geometry.thumbHeight <= 0 || geometry.minimapHeight <= 0) {
      interactionState = MinimapMouseInteractionState.IDLE
      dragOffset = 0
      dragStartY = 0
      dragDistancePx = 0
      return
    }

    interactionState = MinimapMouseInteractionState.DRAGGING
    dragAnimationDisabled = false
    dragStartY = e.y
    dragDistancePx = 0

    val thumbTop = geometry.thumbStart - geometry.areaStart
    val thumbBottom = thumbTop + geometry.thumbHeight

    dragOffset = if (e.y in thumbTop until thumbBottom) {
      e.y - thumbTop
    }
    else {
      geometry.thumbHeight / 2
    }
  }

  override fun mouseReleased(e: MouseEvent) {
    if (e.button != MouseEvent.BUTTON1) return

    if (interactionState == MinimapMouseInteractionState.DRAGGING && dragDistancePx > 0) {
      logDragged()
    }

    if (dragAnimationDisabled) {
      editor.scrollingModel.enableAnimation()
      dragAnimationDisabled = false
    }

    interactionState = MinimapMouseInteractionState.IDLE
    dragOffset = 0
    dragStartY = 0
    dragDistancePx = 0
  }

  override fun mouseWheelMoved(mouseWheelEvent: MouseWheelEvent) {
    val preciseWheelRotation = mouseWheelEvent.preciseWheelRotation
    if (preciseWheelRotation == 0.0) return
    logWheelScrolled(mouseWheelEvent, preciseWheelRotation)

    editor.scrollingModel.scrollVertically(
      editor.scrollingModel.verticalScrollOffset +
        (preciseWheelRotation * editor.lineHeight * WHEEL_SCROLL_LINES).toInt())
  }

  override fun mouseDragged(e: MouseEvent) {
    if (interactionState != MinimapMouseInteractionState.DRAGGING) return

    if (!dragAnimationDisabled) {
      editor.scrollingModel.disableAnimation()
      dragAnimationDisabled = true
    }
    dragDistancePx = max(dragDistancePx, abs(e.y - dragStartY))

    panel.scrollThumbTo(e.y, dragOffset)
  }

  override fun mouseClicked(e: MouseEvent) {
    if (e.button != MouseEvent.BUTTON1) return
    logClicked()
    handleClick(e)
  }

  override fun mouseMoved(e: MouseEvent) {
    panel.cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    updateHover(e.point)
  }

  override fun mouseExited(e: MouseEvent) {
    updateHover(null)
  }

  private fun updateHover(point: Point?) {
    hoverController.updateHover(point)
  }

  private fun handleClick(e: MouseEvent) {
    // TODO: if clicked on structure view element -> scroll to the element
    if (panel.settings.state.rightAligned && tryDispatchToEditorInlay(e)) return
    panel.scrollTo(e.y)
  }

  /**
   * Converts a minimap click to editor content coordinates and dispatches it to the inlay at
   * that position, if one exists. Returns `true` if the event was consumed by an inlay.
   */
  private fun tryDispatchToEditorInlay(e: MouseEvent): Boolean {
    val contentComponent = editor.contentComponent
    val editorPoint = SwingUtilities.convertPoint(panel, e.point, contentComponent)
    val inlay = editor.inlayModel.getElementAt(editorPoint) ?: return false
    val renderer = inlay.renderer
    if (renderer !is InputHandler) return false
    val bounds = inlay.getBounds() ?: return false
    val translated = Point(editorPoint.x - bounds.x, editorPoint.y - bounds.y)
    val syntheticEvent = MouseEvent(
      contentComponent,
      e.id, e.`when`, e.modifiersEx,
      editorPoint.x, editorPoint.y,
      e.clickCount, false, e.button
    )
    renderer.mouseMoved(syntheticEvent, translated)
    renderer.mouseReleased(syntheticEvent, translated)
    renderer.mouseExited()
    return true
  }

  private fun logClicked() {
    val settings = panel.settings.state
    MinimapUsageCollector.logClicked(
      scaleMode = settings.scaleMode,
      rightAligned = settings.rightAligned,
      source = MinimapUsageCollector.InteractionSource.MOUSE,
    )
  }

  private fun logDragged() {
    val settings = panel.settings.state
    MinimapUsageCollector.logDragged(
      scaleMode = settings.scaleMode,
      rightAligned = settings.rightAligned,
      dragDistanceBucket = MinimapUsageCollector.toDragDistanceBucket(dragDistancePx),
      source = MinimapUsageCollector.InteractionSource.MOUSE,
    )
  }

  private fun logWheelScrolled(mouseWheelEvent: MouseWheelEvent, preciseWheelRotation: Double) {
    val settings = panel.settings.state
    MinimapUsageCollector.logWheelScrolled(
      scaleMode = settings.scaleMode,
      direction = if (preciseWheelRotation > 0) {
        MinimapUsageCollector.ScrollDirection.DOWN
      }
      else {
        MinimapUsageCollector.ScrollDirection.UP
      },
      source = wheelInteractionSource(mouseWheelEvent),
    )
  }

  private fun wheelInteractionSource(mouseWheelEvent: MouseWheelEvent): MinimapUsageCollector.InteractionSource {
    val absolutePreciseRotation = abs(mouseWheelEvent.preciseWheelRotation)
    val absoluteWheelRotation = abs(mouseWheelEvent.wheelRotation.toDouble())
    return if (absoluteWheelRotation == 0.0 || absolutePreciseRotation < absoluteWheelRotation) {
      MinimapUsageCollector.InteractionSource.TOUCHPAD
    }
    else {
      MinimapUsageCollector.InteractionSource.MOUSE
    }
  }

  companion object {
    private const val WHEEL_SCROLL_LINES: Int = 5
  }
}
