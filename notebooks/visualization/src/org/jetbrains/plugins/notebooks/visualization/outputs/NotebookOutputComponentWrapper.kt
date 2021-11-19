package org.jetbrains.plugins.notebooks.visualization.outputs

import com.intellij.openapi.extensions.ExtensionPointName
import javax.swing.JPanel

interface NotebookOutputComponentWrapper {
  /**
   * Allows to add new components around notebook cell output components.
   *
   * The [component] is the container that holds cell output's scroll pane. It must have `BorderLayout` as a layout.
   * The center of the layout is already occupied by the scroll pane. All other placeholders are vacant and can be filled
   * with additional components.
   */
  fun wrap(component: JPanel)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<NotebookOutputComponentWrapper> =
      ExtensionPointName.create("org.jetbrains.plugins.notebooks.editor.outputs.notebookOutputComponentWrapper")
  }
}