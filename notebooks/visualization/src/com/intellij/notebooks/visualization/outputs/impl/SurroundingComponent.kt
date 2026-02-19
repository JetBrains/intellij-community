package com.intellij.notebooks.visualization.outputs.impl

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentWrapper
import com.intellij.notebooks.visualization.ui.registerEditorSizeWatcher
import com.intellij.notebooks.visualization.ui.textEditingAreaWidth
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel

internal class SurroundingComponent private constructor(private val innerComponent: InnerComponent) : JPanel(
  BorderLayout()) {
  private var presetWidth = 0

  init {
    isOpaque = true
    border = IdeBorderFactory.createEmptyBorder(JBUI.insetsTop(JBUI.scale(4)))
    add(innerComponent, BorderLayout.CENTER)
  }

  fun fireResize() {
    this.firePropertyChange("preferredSize", null, preferredSize)
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
    fun create(editor: EditorImpl, innerComponent: InnerComponent) = SurroundingComponent(innerComponent).also {
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