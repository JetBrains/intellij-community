// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import kotlin.math.min


class PythonCellSelectionModelProvider : NotebookCellSelectionModelProvider {
  override fun create(editor: Editor): NotebookCellSelectionModel =
    CaretBasedCellSelectionModel(editor)
}

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
      return ReadAction.compute<List<NotebookCellLines.Interval>, Throwable> {
        editor.caretModel.allCarets.flatMap { caret ->
          notebookCellLines.getCells(editor.document.getSelectionLines(caret))
        }.distinct()
      }
    }

  override fun isSelectedCell(cell: NotebookCellLines.Interval): Boolean =
    ReadAction.compute<Boolean, Throwable> {
      editor.caretModel.allCarets.any { caret ->
        editor.document.getSelectionLines(caret).hasIntersectionWith(cell.lines)
      }
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
    val caretModel = editor.caretModel
    caretModel.primaryCaret.moveToLogicalPosition(cell.startLogicalPosition)
    caretModel.primaryCaret.removeSelection()
    caretModel.removeSecondaryCarets()
  }
}

fun Document.getSelectionLines(caret: Caret): IntRange {
  val selectionStart = caret.selectionStart
  val selectionEnd = caret.selectionEnd
  val lastLine = getLineNumber(selectionEnd)

  // See: DS-3659 Context menu action "Delete cell" deletes wrong cell
  if (caret.offset < selectionStart || caret.offset > selectionEnd) {
    val caretLine = getLineNumber(caret.offset)
    return IntRange(caretLine, caretLine)
  }

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