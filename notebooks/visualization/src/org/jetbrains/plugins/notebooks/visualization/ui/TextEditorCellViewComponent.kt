package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import java.awt.Dimension
import java.awt.Rectangle

class TextEditorCellViewComponent(
  private val editor: EditorEx,
  private val cell: EditorCell,
) : EditorCellViewComponent(), HasGutterIcon {

  private var highlighters: List<RangeHighlighter>? = null

  private val interval: NotebookCellLines.Interval
    get() = cell.intervalPointer.get() ?: error("Invalid interval")

  // todo: must be removed once we have a robust way to avoid getting interval for an invalid/deleted cell
  private val safeInterval: NotebookCellLines.Interval?
    get() = cell.intervalPointer.get()

  override fun updateGutterIcons(gutterAction: AnAction?) {
    disposeExistingHighlighter()
    if (gutterAction != null && cell.view?.isValid() == true) {
      val markupModel = editor.markupModel
      val interval = safeInterval ?: return
      val startOffset = editor.document.getLineStartOffset(interval.lines.first)
      val endOffset = editor.document.getLineEndOffset(interval.lines.last)
      val highlighter = markupModel.addRangeHighlighter(
        startOffset,
        endOffset,
        HighlighterLayer.FIRST - 100,
        TextAttributes(),
        HighlighterTargetArea.LINES_IN_RANGE
      )
      highlighter.gutterIconRenderer = ActionToGutterRendererAdapter(gutterAction)
      this.highlighters = listOf(highlighter)
    }
  }

  override fun doDispose() {
    disposeExistingHighlighter()
  }

  private fun disposeExistingHighlighter() {
    if (highlighters != null) {
      highlighters?.forEach {
        it.dispose()
      }
      highlighters = null
    }
  }

  override fun calculateBounds(): Rectangle {
    val interval = interval
    val startOffset = editor.document.getLineStartOffset(interval.lines.first)
    val startLocation = editor.offsetToXY(startOffset)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val endLocation = editor.offsetToXY(endOffset)
    val height = endLocation.y + editor.lineHeight - startLocation.y
    val width = endLocation.x - startLocation.x
    val dimension = Dimension(width, height)
    return Rectangle(startLocation, dimension)
  }
}