// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.Color
import java.awt.geom.Line2D

/**
 * Controller can be used to add custom shapes (such as lines, rectangles, etc.) to the layered pane above the Editor.
 */
class EditorLayerController(editor: EditorImpl) {
  private val viewComponent = editor.scrollPane.viewport.view as EditorComponentWrapper

  fun replaceOverlayLine(oldLine: Line2D?, newline: Line2D, color: Color) {
    viewComponent.replaceOverlayLine(oldLine, newline, color)
  }

  fun removeOverlayLine(line: Line2D) {
    viewComponent.removeOverlayLine(line)
  }

  companion object {
    fun EditorImpl.getLayerController(): EditorLayerController? {
      return DecoratedEditor.get(this)?.editorLayerController
    }
  }
}