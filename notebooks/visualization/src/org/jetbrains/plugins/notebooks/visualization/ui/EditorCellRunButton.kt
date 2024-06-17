package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines


class EditorCellRunButton(private val editor: EditorEx) {
  // PY-72142 & PY-69788 & PY-72701 - adds "Run cell" button to the gutter
  private var cellRangeHighlighter: RangeHighlighter? = null

  fun showRunButton(interval: NotebookCellLines.Interval) {
    if (editor.editorKind == EditorKind.DIFF) return
    hideRunButton()

    if (interval.type != NotebookCellLines.CellType.CODE) return  // PY-73182
    val linesRange = interval.lines

    val sourceStartOffset = editor.document.getLineEndOffset(interval.lines.first)
    val sourceEndOffset = editor.document.getLineEndOffset(interval.lines.last)
    if (sourceStartOffset + 1 == sourceEndOffset) return  // PY-72785 don't show for empty cells

    cellRangeHighlighter = editor.markupModel.addRangeHighlighter(
      sourceStartOffset,
      sourceEndOffset,
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

  fun dispose() = hideRunButton()
}
