package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import kotlin.math.min

class CaretBasedCellSelectionModel(private val editor: Editor) : NotebookCellSelectionModel {
  override val primarySelectedCell: NotebookCellLines.Interval
    get() = editor.getCell(editor.caretModel.primaryCaret.logicalPosition.line)

  override val primarySelectedRegion: List<NotebookCellLines.Interval>
    get() {
      val primary = primarySelectedCell
      return selectedRegions.find { primary in it }!!
    }

  override val selectedRegions: List<List<NotebookCellLines.Interval>>
    get() = groupNeighborCells(selectedCells)

  override val selectedCells: List<NotebookCellLines.Interval>
    get() {
      val notebookCellLines = NotebookCellLines.get(editor)
      return editor.caretModel.allCarets.flatMap { caret ->
        notebookCellLines.getCells(editor.document.getSelectionLines(caret))
      }.distinct()
    }

  override fun isSelectedCell(cell: NotebookCellLines.Interval): Boolean =
    editor.caretModel.allCarets.any { caret ->
      editor.document.getSelectionLines(caret).hasIntersectionWith(cell.lines)
    }

  override fun selectCell(cell: NotebookCellLines.Interval, makePrimary: Boolean) {
    editor.caretModel.addCaret(cell.startLogicalPosition, makePrimary)
  }

  override fun removeSecondarySelections() {
    editor.caretModel.removeSecondaryCarets()
  }

  override fun removeSelection(cell: NotebookCellLines.Interval) {
    for (caret in editor.caretModel.allCarets) {
      if (caret.logicalPosition.line in cell.lines) {
        editor.caretModel.removeCaret(caret)
      }
    }
  }

  override fun selectSingleCell(cell: NotebookCellLines.Interval) {
    // carets merging based on visual position
    // can't add another caret "<caret>#%%\n" -> "#%%\n<caret>", in jupyter visual positions are equal (header is hidden).
    // so better to move primary caret instead of adding new. See CellBorderTest.`one cell, click on add cell above`
    editor.caretModel.primaryCaret.moveToLogicalPosition(cell.startLogicalPosition)
    editor.caretModel.removeSecondaryCarets()
  }
}

private fun Document.getSelectionLines(caret: Caret): IntRange {
  val selectionEnd = caret.selectionEnd
  val lastLine = getLineNumber(selectionEnd)

  if (caret.offset < selectionEnd && getLineStartOffset(lastLine) == selectionEnd) {
    // for example, after triple click on line1
    // #%%
    // <selection><caret>line1
    // </selection>#%%
    // line2
    return IntRange(getLineNumber(caret.selectionStart), lastLine - 1)
  }

  return IntRange(getLineNumber(caret.selectionStart), lastLine)
}

private val NotebookCellLines.Interval.startLogicalPosition: LogicalPosition
  get() = LogicalPosition(min(firstContentLine, lines.last), 0)