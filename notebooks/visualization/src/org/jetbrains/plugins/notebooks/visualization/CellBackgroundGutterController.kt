package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.Graphics
import java.awt.Rectangle

class CellBackgroundGutterController : NotebookCellGutterController {
  override fun paint(editor: EditorImpl,
                     g: Graphics,
                     r: Rectangle,
                     intervalIterator: ListIterator<NotebookCellLines.Interval>,
                     visualLineStart: Int,
                     visualLineEnd: Int,
                     logicalLineStart: Int,
                     logicalLineEnd: Int) {
    val notebookCellLines = NotebookCellLines.get(editor)
    val notebookCellInlayManager = NotebookCellInlayManager.get(editor) ?: throw AssertionError("Register inlay manager first")
    val interval = intervalIterator.next()

    val top = editor.offsetToXY(editor.document.getLineStartOffset(interval.lines.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(interval.lines.last)).y + editor.lineHeight - top

    val appearance = editor.notebookAppearance
    if (interval.type == NotebookCellLines.CellType.CODE) {
      paintNotebookCellBackgroundGutter(editor, g, r, interval, top, height) {
        paintCaretRow(editor, g, interval.lines)
      }
    }
    else {
      if (editor.editorKind == EditorKind.DIFF) return
      paintCaretRow(editor, g, interval.lines)
      appearance.getCellStripeColor(editor, interval)?.let {
        appearance.paintCellStripe(g, r, it, top, height)
      }
    }

    for (controller: NotebookCellInlayController in notebookCellInlayManager.inlaysForInterval(interval)) {
      controller.paintGutter(editor, g, r, notebookCellLines.intervals.listIterator(interval.ordinal))
    }
  }

  private fun paintCaretRow(editor: EditorImpl, g: Graphics, lines: IntRange) {
    if (editor.settings.isCaretRowShown) {
      val caretModel = editor.caretModel
      val caretLine = caretModel.logicalPosition.line
      if (caretLine in lines) {
        g.color = editor.colorsScheme.getColor(EditorColors.CARET_ROW_COLOR)
        g.fillRect(
          0,
          editor.visualLineToY(caretModel.visualPosition.line),
          g.clipBounds.width,
          editor.lineHeight
        )
      }
    }
  }
}