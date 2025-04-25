// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearance.Companion.NOTEBOOK_APPEARANCE_KEY
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

object NotebookUtil {
  val Editor.notebookAppearance: NotebookEditorAppearance
    get() = NOTEBOOK_APPEARANCE_KEY.get(this)!!

  fun paintNotebookCellBackgroundGutter(
    editor: EditorImpl,
    g: Graphics,
    r: Rectangle,
    top: Int,
    height: Int,
    presentationModeMasking: Boolean = false,  // PY-74597
    customColor: Color? = null,
    actionBetweenBackgroundAndStripe: () -> Unit = {},
  ) {
    val diffViewOffset = 6  // randomly picked a number that fits well
    val appearance = editor.notebookAppearance
    val borderWidth = appearance.getLeftBorderWidth()
    val gutterWidth = editor.gutterComponentEx.width

    val (fillX, fillWidth, fillColor) = if (!presentationModeMasking)
      Triple(r.width - borderWidth + 1, borderWidth, customColor ?: appearance.codeCellBackgroundColor.get())
    else
      Triple(r.width - borderWidth - gutterWidth, gutterWidth, editor.colorsScheme.defaultBackground)

    g.color = fillColor

    when (editor.editorKind == EditorKind.DIFF) {
      true -> g.fillRect(fillX + diffViewOffset, top, fillWidth - diffViewOffset, height)
      else -> g.fillRect(fillX, top, fillWidth, height)
    }

    actionBetweenBackgroundAndStripe()
  }
}
