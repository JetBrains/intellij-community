package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.codeVisionEntryMouseEventKey
import com.intellij.codeInsight.codeVision.ui.*
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionRenderer
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionTheme
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.rd.createLifetime
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.asProperty
import com.jetbrains.rd.util.debounceNotNull
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.adviseWithPrev
import com.jetbrains.rd.util.reactive.map
import com.jetbrains.rd.util.reactive.viewNotNull
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent
import java.time.Duration
import javax.swing.JLabel
import javax.swing.SwingUtilities

class CodeVisionSelectionController private constructor(val lifetime: Lifetime,
                                                        val editor: EditorImpl,
                                                        val projectModel: ProjectCodeVisionModel) {

  companion object {
    val map = HashMap<Editor, CodeVisionSelectionController>()
    val logger = logger<CodeVisionSelectionController>()

    fun install(editor: EditorImpl, projectModel: ProjectCodeVisionModel) {
      var controller = map[editor]
      if (controller != null) return

      val lifetime = editor.disposable.createLifetime()
      controller = CodeVisionSelectionController(lifetime, editor, projectModel)
      map[editor] = controller

      lifetime.onTermination {
        map.remove(editor)
      }
    }
  }

  private val hovered = projectModel.hoveredInlay
  private val hoveredEntry = projectModel.hoveredEntry
  private val lensPopupActive = projectModel.lensPopupActive

  init {
    editor.contentComponent.windowAncestor().map { it != null }.view(lifetime) { ltmain, inHierarchy ->
      if (inHierarchy) {
        hovered.adviseWithPrev(ltmain) { previousInlay, currentInlay ->
          previousInlay.asNullable?.repaint()
          currentInlay?.repaint()
        }

        hovered.view(ltmain) { lt, _ ->
          hoveredEntry.view(lt) { entryLifetime, _ ->
            hovered.value?.repaint()
            editor.mousePressed().advise(entryLifetime) {
              entryPressHandler(it)
            }

            editor.mouseReleased().advise(entryLifetime) {
              val mouseEvent: MouseEvent = it.mouseEvent
              checkEditorMousePosition(mouseEvent.point) ?: return@advise

              if (mouseEvent.isPopupTrigger) it.consume()
            }
          }

          lensPopupActive.view(lt) { activePopupLifetime, popupActive ->
            if (!popupActive) {
              hoveredEntry.debounceNotNull(Duration.ofMillis(1000), SwingScheduler).asProperty(null)
                .viewNotNull(activePopupLifetime) { lt1, it ->
                  showTooltip(lt1, it)
                }
            }
          }
        }

        hoveredEntry.map { it != null }.advise(ltmain) { hasHoveredEntry ->
          updateCursor(hasHoveredEntry)
        }

        editor.mousePoint().advise(ltmain) {
          checkEditorMousePosition(it)
        }

      }
      else if (hovered.value?.editor == editor) {
        clearHovered()
      }
    }
  }

  private fun entryPressHandler(event: EditorMouseEvent) {
    val mouseEvent: MouseEvent = event.mouseEvent

    val entry = checkEditorMousePosition(mouseEvent.point) ?: return
    editor.contentComponent.requestFocus()
    event.consume()

    val rangeLensesModel = hovered.value?.getUserData(CodeVisionListData.KEY)?.rangeCodeVisionModel ?: return

    entry.putUserData(codeVisionEntryMouseEventKey, mouseEvent)
    if (SwingUtilities.isLeftMouseButton(mouseEvent)) {
      logger.trace { "entryPressHandler :: isLeftMouseButton" }
      rangeLensesModel.handleLensClick(entry)
    }
    else if (SwingUtilities.isRightMouseButton(mouseEvent)) {
      logger.trace { "entryPressHandler :: isRightMouseButton" }
      rangeLensesModel.handleLensRightClick()
    }
  }

  private fun updateCursor(hasHoveredEntry: Boolean) {
    val cursor =
      if (hasHoveredEntry)
        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      else
        Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)

    if (editor.contentComponent.cursor != cursor)
      UIUtil.setCursor(editor.contentComponent, cursor)
  }

  private fun showTooltip(lt: Lifetime, entry: CodeVisionEntry) {
    val text = entry.tooltipText()
    if (text.isEmpty()) return

    val ld = lt.createNested()

    val inlay = hovered.value ?: return
    val inlayBounds = inlay.bounds ?: return

    val renderer = inlay.renderer as CodeVisionRenderer
    val entryBounds = renderer.entryBounds(inlay, entry) ?: return

    val x = inlayBounds.x + entryBounds.x + (entryBounds.width / 2)
    val y = inlayBounds.y + (inlayBounds.height / 2)

    val contentComponent = inlay.editor.contentComponent
    val component = inlay.editor.component
    val relativePoint = RelativePoint(contentComponent, Point(x, y))

    val tooltip = IdeTooltip(component, relativePoint.getPoint(component), JLabel(text))
    val currentTooltip = IdeTooltipManager.getInstance().show(tooltip, false, false)

    inlay.editor.scrollingModel.visibleAreaChanged().advise(ld) {
      ld.terminate()
    }

    ld.onTermination { currentTooltip.hide() }
  }

  private fun checkEditorMousePosition(editorMousePosition: Point?): CodeVisionEntry? {
    if (editorMousePosition == null) {
      clearHovered()
      return null
    }

    val hoveredInlay = editor.inlayModel.getElementAt(editorMousePosition)
    return updateHovered(hoveredInlay, editorMousePosition)
  }

  private fun updateHovered(hoveredInlay: Inlay<EditorCustomElementRenderer>?, mouseOnInlay: Point?): CodeVisionEntry? {
    if (mouseOnInlay == null || hoveredInlay == null || hoveredInlay.renderer !is CodeVisionRenderer) {
      clearHovered()
      return null
    }

    val bounds = hoveredInlay.bounds ?: run {
      clearHovered()
      return null
    }

    if (CodeVisionTheme.yInInlayBounds(mouseOnInlay.y, bounds)) {
      val renderer = hoveredInlay.renderer as CodeVisionRenderer
      val entry = renderer.hoveredEntry(hoveredInlay, mouseOnInlay.x - bounds.x, mouseOnInlay.y - bounds.y)

      hovered.set(hoveredInlay)
      hoveredEntry.set(entry)
      return entry
    }
    clearHovered()
    return null
  }

  private fun clearHovered() {
    hovered.set(null)
    hoveredEntry.set(null)
  }
}