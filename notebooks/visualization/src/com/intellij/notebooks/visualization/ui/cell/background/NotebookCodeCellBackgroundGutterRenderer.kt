// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.background

import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.UISettings
import com.intellij.notebooks.ui.visualization.NotebookUtil
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookLineMarkerRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.Graphics
import java.awt.Rectangle

/**
 * Draws a vertical gray rectangle in the gutter
 * between the line numbers and the text.
 */
class NotebookCodeCellBackgroundGutterRenderer(
  private val controller: NotebookCellBackgroundController,
) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val (top, height) = controller.getOrCalculateBounds() ?: return

    val isPresentingMode = UISettings.Companion.getInstance().presentationMode || DistractionFreeModeController.isDistractionFreeModeEnabled()
    NotebookUtil.paintNotebookCellBackgroundGutter(editor, g, r, top, height, isPresentingMode)
  }

}