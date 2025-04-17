// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.NotebookIntervalPointer
import com.intellij.notebooks.visualization.NotebookVisualizationCoroutine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean

class NotebookPositionKeeper(val editor: EditorImpl) : Disposable.Default {
  private var forgetJob: Job? = null
  private val internalScroll = AtomicBoolean(false)
  private val visibleArea
    get() = editor.scrollingModel.visibleAreaOnScrollingFinished

  init {
    editor.document.addDocumentListener(object : DocumentListener {
      override fun bulkUpdateStarting(document: Document) {
        forgetToKeepCell()
      }

      override fun beforeDocumentChange(event: DocumentEvent) {
        forgetToKeepCell()
      }
    }, this)
    editor.scrollingModel.addVisibleAreaListener(
      {
        if (it.oldRectangle.y == it.newRectangle.y)
          return@addVisibleAreaListener
        if (!internalScroll.get()) {
          forgetToKeepCell()
        }
      }, this)
  }

  /**
   * Keep position to save cell or upper part of it visible during any changes
   */
  fun rememberRunNextCellScrollKeep(intervalPointer: NotebookIntervalPointer) {
    forgetJob?.cancel()
    forgetJob = null
    forgetToKeepCell()

    intervalPointer.get() ?: return
    editor.putUserData(RUN_NEXT_CELL_SCROLL_KEEPER_INTERVAL, intervalPointer)
  }

  /**
   * Keep position to save cell or upper part of it visible during any changes
   */
  fun forgetToKeepCell() {
    forgetJob?.cancel()
    forgetJob = null
    editor.removeUserData(RUN_NEXT_CELL_SCROLL_KEEPER_INTERVAL)
  }

  /**
   * Some outputs can be not updated immediately we give them some time to finish updates
   */
  fun forgetToKeepCellWithDelay() {
    forgetJob?.cancel()
    forgetJob = NotebookVisualizationCoroutine.Utils.launchBackground {
      delay(1500)
      forgetToKeepCell()
    }
  }

  fun scrollToAppearCell(intervalPointer: NotebookIntervalPointer) {
    val y = calculateScrollToMakeCellVisible(intervalPointer) ?: return
    scrollToY(y)
  }

  fun scrollToKeepCell(maximumHeight: Int? = null): Boolean {
    val intervalPointer = editor.getUserData(RUN_NEXT_CELL_SCROLL_KEEPER_INTERVAL) ?: return false

    val y = calculateScrollToMakeCellVisible(intervalPointer, maximumHeight) ?: return false
    scrollToY(y)
    return true
  }

  /**
   * We need to show whole cell if possible or if it is too big 1/4 of the screen.
   * Constant is based on my sense of the UI
   */
  private fun calculateRangeToKeepCellVisible(intervalPointer: NotebookIntervalPointer, maximumHeight: Int? = null): Rectangle? {
    val editorCell = NotebookCellInlayManager.get(editor)?.getCell(intervalPointer)
    val bounds = editorCell?.view?.calculateBounds() ?: return null
    val visibleAreaHeight = (visibleArea.height * MAXIMUM_HEIGH_OF_SCROLL_KEEPING_CELL).toInt()
    val height = minOf(maximumHeight ?: Int.MAX_VALUE, visibleAreaHeight, bounds.height)
    return Rectangle(bounds.x, bounds.y, bounds.width, height)
  }

  private fun calculateScrollToMakeCellVisible(intervalPointer: NotebookIntervalPointer, maximumHeight: Int? = null): Int? {
    val cellRectangle = calculateRangeToKeepCellVisible(intervalPointer, maximumHeight) ?: return null
    val visibleArea = visibleArea
    if (visibleArea.contains(cellRectangle)) {
      //View screen already contains required cell
      return null
    }

    val bottomCellY = cellRectangle.y + cellRectangle.height
    val scrollToPosition = maxOf(0, bottomCellY - visibleArea.height)
    return scrollToPosition
  }

  fun getPosition(useCaretPositon: Boolean = true): Position {
    val visibleArea = visibleArea

    val topLeftCornerOffset = if (useCaretPositon) {
      val caretY = editor.visualLineToY(editor.caretModel.visualPosition.line)
      if (visibleArea.height > 0 && (caretY + editor.lineHeight <= visibleArea.y || caretY >= (visibleArea.y + visibleArea.height))) {
        editor.logicalPositionToOffset(editor.xyToLogicalPosition(visibleArea.location))
      }
      else {
        editor.caretModel.offset
      }
    }
    else {
      editor.logicalPositionToOffset(editor.xyToLogicalPosition(visibleArea.location))
    }
    val viewportShift = editor.offsetToXY(topLeftCornerOffset).y - visibleArea.y
    val position = Position(topLeftCornerOffset, viewportShift)
    return position
  }

  fun restorePosition(position: Position) {
    val (topLeftCornerOffset, viewportShift) = position
    val newY = editor.offsetToXY(topLeftCornerOffset).y - viewportShift
    if (visibleArea.y == newY)
      return
    scrollToY(-newY)
  }

  private fun scrollToY(newY: Int) {
    if (visibleArea.y == newY)
      return
    EDT.assertIsEdt()
    val scrollingModel = editor.scrollingModel
    try {
      internalScroll.set(true)
      scrollingModel.disableAnimation()
      scrollingModel.scrollVertically(newY)
    }
    finally {
      internalScroll.set(false)
      scrollingModel.enableAnimation()
    }
  }

  fun <T> keepScrollingPositionWhile(task: () -> T): T {
    return WriteIntentReadAction.compute<T, Nothing> {
      if (editor.isDisposed) {
        return@compute task()
      }
      val position = getPosition(false)
      val (r, newOffset) = getOffsetProvider(position).use { offsetProvider ->
        task() to offsetProvider.getOffset()
      }
      if (scrollToKeepCell(null)) {
        return@compute r
      }
      restorePosition(Position(newOffset, position.viewportShift))
      r
    }
  }

  private fun getOffsetProvider(position: Position): OffsetProvider {
    return if (editor.caretModel.offset == position.topLeftCornerOffset) {
      object : OffsetProvider {
        override fun getOffset(): Int = editor.caretModel.offset

        override fun close() {}
      }
    }
    else {
      object : OffsetProvider {
        val myTopLeftCornerMarker = editor.document.createRangeMarker(position.topLeftCornerOffset, position.topLeftCornerOffset)

        override fun getOffset(): Int = myTopLeftCornerMarker.startOffset

        override fun close() {
          myTopLeftCornerMarker.dispose()
        }
      }
    }
  }

  private interface OffsetProvider : AutoCloseable {
    fun getOffset(): Int
  }


  data class Position(val topLeftCornerOffset: Int, val viewportShift: Int)

  companion object {
    private val RUN_NEXT_CELL_SCROLL_KEEPER_INTERVAL = Key<NotebookIntervalPointer>("RUN_NEXT_CELL_SCROLL_KEEPER")
    private const val MAXIMUM_HEIGH_OF_SCROLL_KEEPING_CELL = 0.25
  }
}