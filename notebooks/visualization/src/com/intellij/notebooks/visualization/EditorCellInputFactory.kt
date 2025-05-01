package com.intellij.notebooks.visualization

import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.EditorCellViewComponent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Marker interface for factories producing custom editors for cells
 */
interface EditorCellInputFactory {
  fun createComponent(editor: EditorImpl, cell: EditorCell): EditorCellViewComponent

  fun supports(editor: EditorImpl, cell: EditorCell): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorCellInputFactory> = ExtensionPointName.create<EditorCellInputFactory>("org.jetbrains.plugins.notebooks.inputFactory")
  }
}
