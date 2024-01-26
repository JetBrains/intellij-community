// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.LineNumberConverter
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearance.Companion.NOTEBOOK_APPEARANCE_KEY
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle


val Editor.notebookAppearance: NotebookEditorAppearance
  get() = NOTEBOOK_APPEARANCE_KEY.get(this)!!


inline fun paintNotebookCellBackgroundGutter(
  editor: EditorImpl,
  g: Graphics,
  r: Rectangle,
  lines: IntRange,
  top: Int,
  height: Int,
  crossinline actionBetweenBackgroundAndStripe: () -> Unit = {}
) {
  val diffViewOffset = 6  // randomly picked a number that fits well
  val appearance = editor.notebookAppearance
  val stripe = appearance.getCellStripeColor(editor, lines)
  val stripeHover = appearance.getCellStripeHoverColor(editor, lines)
  val borderWidth = appearance.getLeftBorderWidth()
  val rectBorderCellX = r.width - borderWidth

  g.color = appearance.getCodeCellBackground(editor.colorsScheme)

  if (editor.editorKind == EditorKind.DIFF) {
    g.fillRect(rectBorderCellX + diffViewOffset, top, borderWidth - diffViewOffset, height)
  } else {
    g.fillRect(rectBorderCellX, top, borderWidth, height)
  }

  actionBetweenBackgroundAndStripe()
  if (editor.editorKind == EditorKind.DIFF) return
  if (stripe != null) {
    paintCellStripe(appearance, g, r, stripe, top, height)
  }
  if (stripeHover != null) {
    g.color = stripeHover
    g.fillRect(r.width - appearance.getLeftBorderWidth(), top, appearance.getCellLeftLineHoverWidth(), height)
  }
}

fun paintCellStripe(
  appearance: NotebookEditorAppearance,
  g: Graphics,
  r: Rectangle,
  stripe: Color,
  top: Int,
  height: Int,
) {
  g.color = stripe
  g.fillRect(r.width - appearance.getLeftBorderWidth(), top, appearance.getCellLeftLineWidth(), height)
}

/**
 * Paints green or blue stripe depending on a cell type
 */
fun paintCellGutter(inlayBounds: Rectangle,
                    lines: IntRange,
                    editor: EditorImpl,
                    g: Graphics,
                    r: Rectangle) {
  val appearance = editor.notebookAppearance
  appearance.getCellStripeColor(editor, lines)?.let { stripeColor ->
    paintCellStripe(appearance, g, r, stripeColor, inlayBounds.y, inlayBounds.height)
  }
}

fun paintCaretRow(editor: EditorImpl, g: Graphics, lines: IntRange) {
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

fun installNotebookEditorView(editor: Editor) {
  if (editor is EditorEx) {
    editor.gutterComponentEx.setLineNumberConverter(object : LineNumberConverter {
      override fun convert(editor: Editor, lineNumber: Int): Int? = null
      override fun getMaxLineNumber(editor: Editor): Int? = null
    })
  }
}

fun getJupyterCellSpacing(editor: Editor): Int = editor.getLineHeight()
