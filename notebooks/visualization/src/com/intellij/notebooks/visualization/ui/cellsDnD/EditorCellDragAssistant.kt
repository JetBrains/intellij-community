// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cellsDnD

import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.NotebookVisualizationCoroutine
import com.intellij.notebooks.visualization.getCell
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.EditorCellInput
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.jetbrains.annotations.Nls
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

class EditorCellDragAssistant(
  private val editor: EditorImpl,
  private val cellInput: EditorCellInput,
  private val foldInput: () -> Unit,
  private val unfoldInput: () -> Unit,
) : Disposable {
  private var currentComponent: JComponent? = null

  var isDragging: Boolean = false
    private set

  private var dragStartPoint: Point? = null

  private var currentlyHighlightedCell: CellDropTarget = CellDropTarget.NoCell
  private var dragPreview: CellDragCellPreviewWindow? = null

  private var wasFolded: Boolean = false
  private var inputFoldedState: Boolean = false
  private var outputInitialStates: MutableMap<Int, Boolean> = mutableMapOf()

  private val inlayManager = NotebookCellInlayManager.get(editor)
  private var keyEventDispatcher: KeyEventDispatcher? = null

  /** 0 - no scrolling, 1 - scroll down, -1 - scroll up */
  var scrollingDirection: Int = 0

  /*
   * When the dragged item is close to the editor top or bottom, auto-scrolling is performed in the background via this job.
   * Work with this Job is always in EDT, so here is no need to protect anything from concurrency.
   */
  var scrollingJob: Job? = null

  fun initDrag(e: MouseEvent) {
    isDragging = true
    dragStartPoint = e.locationOnScreen
    attachKeyEventDispatcher()
  }

  private fun attachKeyEventDispatcher() {
    if (keyEventDispatcher == null) {
      keyEventDispatcher = KeyEventDispatcher { keyEvent ->
        if (isDragging) {
          when (keyEvent.id) {
            KeyEvent.KEY_PRESSED -> {
              handleKeyPressedDuringDrag(keyEvent)
              return@KeyEventDispatcher false
            }
          }
        }
        false
      }

      KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .addKeyEventDispatcher(keyEventDispatcher)
    }
  }

  private fun handleKeyPressedDuringDrag(keyEvent: KeyEvent) {
    when (keyEvent.keyCode) {
      KeyEvent.VK_ESCAPE,
      KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
        -> cancelDrag()
    }
  }

  private fun cancelDrag() {
    deleteDragPreview()
    clearDragState()
    unfoldCellIfNeeded()
    removeKeyEventDispatcher()
  }

  private fun removeKeyEventDispatcher() {
    keyEventDispatcher?.let {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(it)
      keyEventDispatcher = null
    }
  }

  fun updateDragOperation(e: MouseEvent) {
    if (!isDragging) return

    if (dragPreview == null) {
      dragPreview = CellDragCellPreviewWindow(getPlaceholderText(), editor)
      dragPreview?.isVisible = true
      WindowManager.getInstance().setAlphaModeRatio(dragPreview, 0.2f)
      foldDraggedCellIfNeeded()
    }

    dragPreview?.followCursor(e.locationOnScreen)
    val currentLocation = e.locationOnScreen
    updateDragVisuals(currentLocation)

    scrollEditorIfNearBounds(e)
  }

  fun scrollEditorIfNearBounds(e: MouseEvent) {
    val editorPoint = SwingUtilities.convertPoint(e.component, e.point, editor.component)
    val scrollBarModel = editor.scrollPane.verticalScrollBar.model
    val sensitiveZoneHeight = (editor.component.height * 0.05).toInt()
    if (editorPoint.y < sensitiveZoneHeight && scrollBarModel.value > scrollBarModel.minimum) {
      startScrolling(-1)
    }
    else if (editorPoint.y > editor.component.height - sensitiveZoneHeight && scrollBarModel.value < scrollBarModel.maximum) {
      startScrolling(1)
    }
    else {
      scrollingJob?.cancel()
      scrollingJob = null
      scrollingDirection = 0
    }
  }

  fun startScrolling(delta: Int) {
    if (scrollingDirection == delta) return
    scrollingJob?.cancel()
    scrollingDirection = delta
    val speed = editor.component.height * 0.01

    scrollingJob = NotebookVisualizationCoroutine.Utils.launchEdt {
      while (isDragging) {
        delay(10)
        editor.scrollingModel.scrollVertically((editor.scrollingModel.verticalScrollOffset + delta * speed).toInt())
      }
    }.apply { cancelOnDispose(this@EditorCellDragAssistant) }
  }

  fun finishDrag(e: MouseEvent) {
    deleteDragPreview()
    removeKeyEventDispatcher()
    scrollingJob?.cancel()

    if (!isDragging || dragStartPoint == null) {
      isDragging = false
      return
    }

    val dragDistance = e.locationOnScreen.distance(dragStartPoint!!)
    if (dragDistance < MINIMAL_DRAG_DISTANCE) {
      clearDragState()
      unfoldCellIfNeeded()
      return
    }

    clearDragState()
    val targetCell = retrieveTargetCell(e)
    unfoldCellIfNeeded()

    ApplicationManager.getApplication().messageBus
      .syncPublisher(CellDropNotifier.getTopicForEditor(editor))
      .cellDropped(CellDropEvent(cellInput.cell, targetCell))
  }

  private fun getCellUnderCursor(editorPoint: Point): CellDropTarget {
    val notebookCellManager = NotebookCellInlayManager.get(editor) ?: return CellDropTarget.NoCell

    notebookCellManager.getCell(notebookCellManager.cells.lastIndex).view?.calculateBounds()?.let { lastCellBounds ->
      if (editorPoint.y > lastCellBounds.maxY) return CellDropTarget.BelowLastCell
    }

    val line = editor.document.getLineNumber(editor.xyToLogicalPosition(editorPoint).let(editor::logicalPositionToOffset))
    val realCell = notebookCellManager.getCell(editor.getCell(line).ordinal)
    return CellDropTarget.TargetCell(realCell)
  }

  private fun retrieveTargetCell(e: MouseEvent): CellDropTarget {
    val dropLocation = e.locationOnScreen
    val editorLocationOnScreen = editor.contentComponent.locationOnScreen
    val x = dropLocation.x - editorLocationOnScreen.x
    val y = dropLocation.y - editorLocationOnScreen.y
    val editorPoint = Point(x, y)

    return getCellUnderCursor(editorPoint)
  }

  private fun updateDragVisuals(currentLocationOnScreen: Point) {
    val editorLocationOnScreen = editor.contentComponent.locationOnScreen
    val x = currentLocationOnScreen.x - editorLocationOnScreen.x
    val y = currentLocationOnScreen.y - editorLocationOnScreen.y

    val cellUnderCursor = getCellUnderCursor(Point(x, y))
    updateDropIndicator(cellUnderCursor)
  }

  private fun foldDraggedCellIfNeeded() {
    // We dont need to fold small cell. 10 lines - chosen empirically and can be changed.
    val cellLines = cellInput.cell.interval.lines
    if (cellLines.last - cellLines.first < 10) {
      return
    }
    inputFoldedState = cellInput.folded
    if (!inputFoldedState) foldInput()

    cellInput.cell.view?.outputs?.outputs?.forEachIndexed { index, output ->
      outputInitialStates[index] = output.collapsed
      output.collapsed = true
    }
    wasFolded = true
  }

  private fun unfoldCellIfNeeded() {
    if (!wasFolded) return
    if (!inputFoldedState) unfoldInput()

    cellInput.cell.view?.outputs?.outputs?.forEachIndexed { index, output ->
      output.collapsed = outputInitialStates[index] == true
    }
    outputInitialStates.clear()
    wasFolded = false
  }

  private fun deleteDropIndicator() = when (currentlyHighlightedCell) {
    is CellDropTarget.TargetCell -> deleteDropIndicatorForTargetCell((currentlyHighlightedCell as CellDropTarget.TargetCell).cell)
    CellDropTarget.BelowLastCell -> removeHighlightAfterLastCell()
    else -> {}
  }

  private fun updateDropIndicator(targetCell: CellDropTarget) {
    deleteDropIndicator()
    currentlyHighlightedCell = targetCell

    when (targetCell) {
      is CellDropTarget.TargetCell -> targetCell.cell.view?.addDropHighlightIfApplicable()
      CellDropTarget.BelowLastCell -> addHighlightAfterLastCell()
      else -> {}
    }
  }

  private fun addHighlightAfterLastCell() = inlayManager?.endNotebookInlays?.filterIsInstance<DropHighlightable>()?.forEach {
    it.addDropHighlight()
  }

  private fun removeHighlightAfterLastCell() = inlayManager?.endNotebookInlays?.filterIsInstance<DropHighlightable>()?.forEach {
    it.removeDropHighlight()
  }

  private fun deleteDropIndicatorForTargetCell(cell: EditorCell) = try {
    cell.view?.removeDropHighlightIfPresent()
  }
  catch (e: NullPointerException) {
    // cell.view? uses !! to get inlay manager, it may be already disposed - so nothing to delete here anyway
    thisLogger().warn("Error removing drop highlight, NotebookCellInlayManager is already disposed", e)
  }

  private fun clearDragState() {
    isDragging = false
    deleteDropIndicator()
  }

  private fun deleteDragPreview() {
    dragPreview?.dispose()
    dragPreview = null
  }

  override fun dispose() {
    removeKeyEventDispatcher()
    deleteDropIndicator()
    deleteDragPreview()
    unfoldCellIfNeeded()
    clearDragState()
    currentComponent = null
  }

  @Nls
  private fun getPlaceholderText(): String {
    @NlsSafe val firstNotEmptyString = cellInput.cell.source.get().lines().firstOrNull { it.trim().isNotEmpty() }
    return StringUtil.shortenTextWithEllipsis(firstNotEmptyString ?: "\u2026", MAX_PREVIEW_TEXT_LENGTH, 0)
  }

  companion object {
    private const val MINIMAL_DRAG_DISTANCE = 8
    private const val MAX_PREVIEW_TEXT_LENGTH = 20
  }
}