package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.UpdateContext
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.text.lines

class TextEditorCellViewComponent(
  private val editor: EditorEx,
  private val cell: EditorCell,
) : EditorCellViewComponent(), HasGutterIcon, InputComponent {

  private var highlighters: List<RangeHighlighter>? = null

  private val interval: NotebookCellLines.Interval
    get() = cell.intervalPointer.get() ?: error("Invalid interval")

  // todo: must be removed once we have a robust way to avoid getting interval for an invalid/deleted cell
  private val safeInterval: NotebookCellLines.Interval?
    get() = cell.intervalPointer.get()

  override fun updateGutterIcons(gutterAction: AnAction?) {
    disposeExistingHighlighter()
    if (gutterAction != null) {
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

  override fun updateFolding(ctx: UpdateContext, folded: Boolean) {
    val interval = interval
    val startOffset = editor.document.getLineStartOffset(interval.lines.first + 1)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val foldingModel = editor.foldingModel
    val currentFoldingRegion = foldingModel.getFoldRegion(startOffset, endOffset)
    if (currentFoldingRegion == null) {
      ctx.addFoldingOperation {
        val text = editor.document.getText(TextRange(startOffset, endOffset))
        val firstNotEmptyString = text.lines().firstOrNull { it.trim().isNotEmpty() }
        val placeholder = StringUtil.shortenTextWithEllipsis(firstNotEmptyString ?: "\u2026", 20, 0)
        foldingModel.createFoldRegion(startOffset, endOffset, placeholder, null, false)?.apply {
          FoldingModelImpl.hideGutterRendererForCollapsedRegion(this)
          isExpanded = false
        }
      }
    }
    else {
      ctx.addFoldingOperation {
        if (currentFoldingRegion.isExpanded) {
          currentFoldingRegion.isExpanded = false
        }
        else {
          foldingModel.removeFoldRegion(currentFoldingRegion)
        }
      }
    }
  }

  override fun requestCaret() {
    val lines = cell.interval.lines
    val offset = editor.document.getLineStartOffset(lines.first + 1)
    editor.caretModel.moveToOffset(offset)
  }
}