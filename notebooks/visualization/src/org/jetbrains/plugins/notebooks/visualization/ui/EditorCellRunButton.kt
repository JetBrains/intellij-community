package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines


class EditorCellRunButton(private val editor: EditorEx) {
  private var cellRangeHighlighter: RangeHighlighter? = null

  fun showRunButton(interval: NotebookCellLines.Interval, type: NotebookCellLines.CellType) {
    hideRunButton()
    if (type != NotebookCellLines.CellType.CODE) return

    val linesRange = interval.lines

    val startOffset = editor.document.getLineStartOffset(linesRange.first)
    val endOffset = editor.document.getLineEndOffset(linesRange.last)

    cellRangeHighlighter = editor.markupModel.addRangeHighlighter(
      startOffset,
      endOffset,
      HighlighterLayer.ERROR + 1,
      null,
      HighlighterTargetArea.LINES_IN_RANGE
    )
    cellRangeHighlighter?.gutterIconRenderer = EditorRunCellGutterIconRenderer(linesRange)
  }

  fun hideRunButton() {
    cellRangeHighlighter?.let {
      editor.markupModel.removeHighlighter(it)
      cellRangeHighlighter = null
    }
  }

  fun dispose() {
    hideRunButton()
  }
}
