// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

object NotebookUiUtils {
  fun getEditorPoint(editorImpl: EditorImpl, e: MouseEvent): Point? {
    val component = if (SwingUtilities.isDescendingFrom(e.component, editorImpl.contentComponent)) {
      editorImpl.contentComponent
    }
    else if (SwingUtilities.isDescendingFrom(e.component, editorImpl.gutterComponentEx)) {
      editorImpl.gutterComponentEx
    }
    else {
      null
    }
    return if (component != null) {
      SwingUtilities.convertPoint(e.component, e.point, component)
    }
    else {
      null
    }
  }
}