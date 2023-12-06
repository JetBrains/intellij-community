package org.jetbrains.plugins.notebooks.visualization.outputs.impl

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentWrapper
import org.jetbrains.plugins.notebooks.visualization.outputs.getEditorBackground
import org.jetbrains.plugins.notebooks.visualization.ui.registerEditorSizeWatcher
import org.jetbrains.plugins.notebooks.visualization.ui.textEditingAreaWidth
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JPanel

internal class SurroundingComponent private constructor(private val innerComponent: InnerComponent) : JPanel(
  BorderLayout()) {
  private var presetWidth = 0

  init {
    border = IdeBorderFactory.createEmptyBorder(JBUI.insetsTop(10))
    add(innerComponent, BorderLayout.CENTER)
  }

  fun fireResize() {
    this.firePropertyChange("preferredSize", null, preferredSize)
  }

  override fun updateUI() {
    super.updateUI()
    isOpaque = true
    background = getEditorBackground()
  }

  override fun getPreferredSize(): Dimension = super.getPreferredSize().also {
    it.width = presetWidth
    // No need to show anything for the empty component
    if (innerComponent.preferredSize.height == 0) {
      it.height = 0
    }
  }

  companion object {
    @JvmStatic
    fun create(
      editor: EditorImpl,
      innerComponent: InnerComponent,
    ) = SurroundingComponent(innerComponent).also {
      registerEditorSizeWatcher(it) {
        it.presetWidth = editor.textEditingAreaWidth
        if (it.presetWidth == 0 && GraphicsUtil.isRemoteEnvironment()) {
          it.presetWidth = editor.contentSize.width
        }
        innerComponent.revalidate()
      }

      for (wrapper in NotebookOutputComponentWrapper.EP_NAME.extensionList) {
        wrapper.wrap(it)
      }
    }
  }
}