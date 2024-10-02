package com.intellij.codeInsight.codeVision.ui.renderers

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.codeVisionEntryMouseEventKey
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.codeInsight.codeVision.ui.model.SwingScheduler
import com.intellij.codeInsight.codeVision.ui.model.ZombieCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.tooltipText
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionListPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionTheme
import com.intellij.codeInsight.codeVision.ui.visibleAreaChanged
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.rd.defineNestedLifetime
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.asProperty
import com.jetbrains.rd.util.debounceNotNull
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.Property
import com.jetbrains.rd.util.reactive.viewNotNull
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.time.Duration
import javax.swing.JLabel
import javax.swing.SwingUtilities

@ApiStatus.Internal
abstract class CodeVisionInlayRendererBase(theme: CodeVisionTheme = CodeVisionTheme()) : CodeVisionInlayRenderer {
  private var isHovered = false
  private var hoveredEntry: Property<CodeVisionEntry?> = Property(null)
  protected val painter: CodeVisionListPainter = CodeVisionListPainter(theme = theme)
  protected lateinit var inlay: Inlay<*>


  fun initialize(inlay: Inlay<*>){
    assert(!::inlay.isInitialized) { "Inlay already defined for current renderer" }
    this.inlay = inlay

    val inlayLifetimeDefinition = inlay.defineNestedLifetime()
    hoveredEntry.viewNotNull(inlayLifetimeDefinition.lifetime) { lifetime, _ ->

      hoveredEntry.debounceNotNull(Duration.ofMillis(1000), SwingScheduler).asProperty(null)
        .viewNotNull(lifetime) { tooltipLifetime, tooltipEntry ->
          showTooltip(tooltipLifetime, tooltipEntry)
        }

      updateCursor(true)
      lifetime.onTermination {
        updateCursor(false)
      }
    }
  }

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    if (!inlay.isValid) return

    val userData = inlay.getUserData(CodeVisionListData.KEY)
    userData?.isPainted = true

    painter.paint(
      inlay.editor,
      textAttributes,
      g,
      inlay.getUserData(CodeVisionListData.KEY),
      getPoint(inlay, targetRegion.location),
      userData?.rangeCodeVisionModel?.state() ?: RangeCodeVisionModel.InlayState.NORMAL,
      isHovered && userData?.isMoreLensActive() == true,
      hoveredEntry.value
    )
  }

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    updateMouseState(true, translated)
  }

  override fun mouseExited() {
    updateMouseState(false, null)
  }

  // Consuming to prevent showing context menu
  override fun mousePressed(event: MouseEvent, translated: Point) {
    hoveredEntry.value ?: return
    when {
      event.isShiftDown -> return
      SwingUtilities.isLeftMouseButton(event) -> event.consume()
      SwingUtilities.isRightMouseButton(event) -> event.consume()
    }
  }

  override fun mouseReleased(event: MouseEvent, translated: Point) {
    val clickedEntry = hoveredEntry.value ?: return
    clickedEntry.putUserData(codeVisionEntryMouseEventKey, event)

    when {
      event.isShiftDown -> return
      SwingUtilities.isLeftMouseButton(event) -> handleLeftClick(clickedEntry)
      SwingUtilities.isRightMouseButton(event) -> handleRightClick(clickedEntry)
    }
    event.consume()
  }

  private fun handleRightClick(clickedEntry: CodeVisionEntry) {
    inlay.getUserData(CodeVisionListData.KEY)?.rangeCodeVisionModel?.handleLensRightClick(clickedEntry, inlay)
  }

  private fun handleLeftClick(clickedEntry: CodeVisionEntry) {
    inlay.getUserData(CodeVisionListData.KEY)?.rangeCodeVisionModel?.handleLensClick(clickedEntry, inlay)
  }

  private fun updateMouseState(isHovered: Boolean, point: Point?) {
    this.isHovered = isHovered
    hoveredEntry.set(if (isHovered) getHoveredEntry(point) else null)
    inlay.repaint()
  }

  private fun getHoveredEntry(point: Point?): CodeVisionEntry? {
    val codeVisionListData = inlay.getUserData(CodeVisionListData.KEY)
    val state = codeVisionListData?.rangeCodeVisionModel?.state() ?: RangeCodeVisionModel.InlayState.NORMAL
    val codeVisionEntry = point?.let { painter.hoveredEntry(inlay.editor, state, codeVisionListData, it.x, it.y) }
    if (codeVisionEntry is ZombieCodeVisionEntry) {
      // zombie is not hoverable
      return null
    }
    return codeVisionEntry
  }

  override fun translatePoint(inlayPoint: Point): Point {
    return getPoint(inlay, inlayPoint)
  }

  private fun updateCursor(hasHoveredEntry: Boolean) {
    val cursor =
      if (hasHoveredEntry) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      else Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)

    if (inlay.editor.contentComponent.cursor != cursor)
      UIUtil.setCursor(inlay.editor.contentComponent, cursor)
  }

  protected open fun getPoint(inlay: Inlay<*>, targetPoint: Point): Point = targetPoint

  protected fun inlayState(inlay: Inlay<*>): RangeCodeVisionModel.InlayState =
    inlay.getUserData(CodeVisionListData.KEY)?.rangeCodeVisionModel?.state() ?: RangeCodeVisionModel.InlayState.NORMAL

  private fun showTooltip(tooltipLifetime: Lifetime, entry: CodeVisionEntry) {
    val text = entry.tooltipText()
    if (text.isEmpty()) return

    val inlayBounds = inlay.bounds ?: return
    val entryBounds = calculateCodeVisionEntryBounds(entry) ?: return

    val tooltipLifetimeDefinition = tooltipLifetime.createNested()

    val x = inlayBounds.x + entryBounds.x + (entryBounds.width / 2)
    val y = inlayBounds.y + (inlayBounds.height / 2)

    val contentComponent = inlay.editor.contentComponent
    val component = inlay.editor.component
    val relativePoint = RelativePoint(contentComponent, Point(x, y))
    val tooltip = IdeTooltip(component, relativePoint.getPoint(component), JLabel(text))
    val currentTooltip = IdeTooltipManager.getInstance().show(tooltip, false, false)

    tooltipLifetimeDefinition.onTermination {
      currentTooltip.hide()
    }

    inlay.editor.scrollingModel.visibleAreaChanged().advise(tooltipLifetimeDefinition){
      tooltipLifetimeDefinition.terminate()
    }
  }
}