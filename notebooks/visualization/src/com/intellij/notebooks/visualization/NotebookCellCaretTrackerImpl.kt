package com.intellij.notebooks.visualization

import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookCellCaretTracker
import com.intellij.openapi.editor.Editor

class NotebookCellCaretTrackerImpl : NotebookCellCaretTracker {
  private data class CellId(
    private val intervalPointer: NotebookIntervalPointer,
  ) : NotebookCellCaretTracker.CellId

  private data class SavedPosition(
    val intervalPointer: NotebookIntervalPointer,
    val offsetInCell: Int,
  ) : NotebookCellCaretTracker.CellCaretPosition

  private data class SavedSnapshot(
    override val cellId: NotebookCellCaretTracker.CellId,
    override val positions: List<NotebookCellCaretTracker.CellCaretPosition>,
  ) : NotebookCellCaretTracker.CellCaretSnapshot

  override fun saveCaretPositions(editor: Editor): NotebookCellCaretTracker.CellCaretSnapshot? {
    if (!NotebookCellLines.hasSupport(editor.document)) return null
    val cellLines = NotebookCellLines.get(editor.document)
    val pointerFactory = NotebookIntervalPointerFactory.getOrNull(editor) ?: return null

    val positions = mutableListOf<NotebookCellCaretTracker.CellCaretPosition>()
    for (caret in editor.caretModel.allCarets) {
      val offset = caret.offset
      val line = editor.document.getLineNumber(offset)
      val interval = cellLines.getCellByLineNumber(line) ?: continue
      val pointer = pointerFactory.create(interval)
      val offsetInCell = offset - interval.getCellStartOffset(editor.document)
      positions.add(SavedPosition(pointer, offsetInCell))
    }

    if (positions.isEmpty()) return null
    val currentCellId = getCurrentCellId(editor) ?: return null
    return SavedSnapshot(currentCellId, positions)
  }

  override fun getCurrentCellId(editor: Editor): NotebookCellCaretTracker.CellId? {
    val pointer = getCurrentCellPointer(editor) ?: return null
    return CellId(pointer)
  }

  override fun restoreCaretPositions(editor: Editor, positions: List<NotebookCellCaretTracker.CellCaretPosition>): Boolean {
    if (!NotebookCellLines.hasSupport(editor.document)) return false
    val cellLines = NotebookCellLines.get(editor.document)

    val currentLine = editor.document.getLineNumber(editor.caretModel.offset)
    val currentCell = cellLines.getCellByLineNumber(currentLine)
    val savedPointers = positions.filterIsInstance<SavedPosition>().mapNotNull { it.intervalPointer.get() }

    val isNewCell = currentCell != null && savedPointers.none { it.ordinal == currentCell.ordinal }
    if (isNewCell) {
      return false
    }
    val validOffsets = mutableListOf<Int>()
    for (position in positions.filterIsInstance<SavedPosition>()) {
      val interval = position.intervalPointer.get() ?: continue
      val cellStartOffset = interval.getCellStartOffset(editor.document)
      val offset = cellStartOffset + position.offsetInCell

      if (offset in 0..editor.document.textLength) {
        validOffsets.add(offset)
      }
    }

    if (validOffsets.isEmpty()) return false

    editor.caretModel.moveToOffset(validOffsets.first())
    for (i in 1 until validOffsets.size) {
      editor.caretModel.addCaret(editor.offsetToVisualPosition(validOffsets[i]))
    }
    return true
  }

  private fun getCurrentCellPointer(editor: Editor): NotebookIntervalPointer? {
    if (!NotebookCellLines.hasSupport(editor.document)) return null
    val cellLines = NotebookCellLines.get(editor.document)
    val pointerFactory = NotebookIntervalPointerFactory.getOrNull(editor) ?: return null
    val currentLine = editor.document.getLineNumber(editor.caretModel.offset)
    val currentCell = cellLines.getCellByLineNumber(currentLine) ?: return null
    return pointerFactory.create(currentCell)
  }
}