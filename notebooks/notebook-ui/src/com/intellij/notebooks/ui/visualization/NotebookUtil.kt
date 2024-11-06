// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

import com.intellij.notebooks.ui.isFoldingEnabledKey
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearance.Companion.NOTEBOOK_APPEARANCE_KEY
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.impl.EditorImpl
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
  presentationModeMasking: Boolean = false,  // PY-74597
  crossinline actionBetweenBackgroundAndStripe: () -> Unit = {},
) {
  val diffViewOffset = 6  // randomly picked a number that fits well
  val appearance = editor.notebookAppearance
  val stripe = appearance.getCellStripeColor(editor, lines)
  val stripeHover = appearance.getCellStripeHoverColor(editor, lines)
  val borderWidth = appearance.getLeftBorderWidth()
  val gutterWidth = editor.gutterComponentEx.width

  val (fillX, fillWidth, fillColor) = when (presentationModeMasking) {
    true -> Triple(r.width - borderWidth - gutterWidth, gutterWidth, editor.colorsScheme.defaultBackground)
    else -> Triple(r.width - borderWidth, borderWidth, appearance.getCodeCellBackground(editor.colorsScheme))
  }

  g.color = fillColor

  when (editor.editorKind == EditorKind.DIFF) {
    true -> g.fillRect(fillX + diffViewOffset, top, fillWidth - diffViewOffset, height)
    else -> g.fillRect(fillX, top, fillWidth, height)
  }

  actionBetweenBackgroundAndStripe()
  if (editor.getUserData(isFoldingEnabledKey) != true) {
    if (editor.editorKind == EditorKind.DIFF) return
    if (stripe != null) {
      paintCellStripe(appearance, g, r, stripe, top, height, editor)
    }
    if (stripeHover != null) {
      g.color = stripeHover
      g.fillRect(r.width - appearance.getLeftBorderWidth(), top, appearance.getCellLeftLineHoverWidth(), height)
    }
  }
}

fun paintCellStripe(
  appearance: NotebookEditorAppearance,
  g: Graphics,
  r: Rectangle,
  stripe: Color,
  top: Int,
  height: Int,
  editor: Editor,
) {
  g.color = stripe
  g.fillRect(r.width - appearance.getLeftBorderWidth(), top, appearance.getCellLeftLineWidth(editor), height)
}

/**
 * Paints green or blue stripe depending on a cell type
 */
fun paintCellGutter(
  inlayBounds: Rectangle,
  lines: IntRange,
  editor: EditorImpl,
  g: Graphics,
  r: Rectangle,
) {
  val appearance = editor.notebookAppearance
  appearance.getCellStripeColor(editor, lines)?.let { stripeColor ->
    paintCellStripe(appearance, g, r, stripeColor, inlayBounds.y, inlayBounds.height, editor)
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