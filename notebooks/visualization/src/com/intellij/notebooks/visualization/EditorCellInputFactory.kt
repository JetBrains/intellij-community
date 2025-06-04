package com.intellij.notebooks.visualization

import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.EditorCellViewComponent
import com.intellij.notebooks.visualization.ui.TextEditorCellInputFactory
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Marker interface for factories producing custom editors for cells
 */
interface EditorCellInputFactory {
  fun createComponent(cell: EditorCell): EditorCellViewComponent

  fun supports(cell: EditorCell): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorCellInputFactory> = ExtensionPointName.create<EditorCellInputFactory>("org.jetbrains.plugins.notebooks.inputFactory")


    fun create(cell: EditorCell): EditorCellViewComponent {
      val inputFactory = EP_NAME.extensionsIfPointIsRegistered.firstOrNull { it.supports(cell) } ?: TextEditorCellInputFactory()
      return inputFactory.createComponent(cell)
    }
  }
}
