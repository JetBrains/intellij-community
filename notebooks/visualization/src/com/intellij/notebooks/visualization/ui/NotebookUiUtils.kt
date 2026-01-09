// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

object NotebookUiUtils {
  /**
   * Editor consists of 2 Content and Gutter.
   * getEditorPoint returns point only if the mouse is over the editor content or gutter.
   */
  fun getEditorPoint(editorImpl: EditorImpl, e: MouseEvent): Point? {
    val pointOnEditor = SwingUtilities.convertPoint(e.component, e.point, editorImpl.contentComponent)
    if (editorImpl.contentComponent.contains(pointOnEditor)) return pointOnEditor

    val pointOnGutter = SwingUtilities.convertPoint(e.component, e.point, editorImpl.gutterComponentEx)
    if (editorImpl.gutterComponentEx.contains(pointOnGutter)) return pointOnGutter

    return null
  }
}