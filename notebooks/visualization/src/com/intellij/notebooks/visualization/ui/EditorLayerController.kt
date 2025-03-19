// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key
import java.awt.Color
import java.awt.geom.Line2D

/**
 * Controller can be used to add custom shapes (such as lines, rectangles, etc.) to the layered pane above the Editor.
 */
class EditorLayerController(private val wrapper: DecoratedEditor.EditorComponentWrapper) {

  fun addOverlayLine(line: Line2D, color: Color) {
    ApplicationManager.getApplication().invokeLater {
      wrapper.addOverlayLine(line, color)
    }
  }

  fun removeOverlayLine(line: Line2D) {
    ApplicationManager.getApplication().invokeLater {
      wrapper.removeOverlayLine(line)
    }
  }

  companion object {
    val EDITOR_LAYER_CONTROLLER_KEY: Key<EditorLayerController?> = Key.create<EditorLayerController>("EditorLayerController")

    fun EditorImpl.getLayerController(): EditorLayerController? {
      return this.getUserData(EDITOR_LAYER_CONTROLLER_KEY)
    }
  }
}