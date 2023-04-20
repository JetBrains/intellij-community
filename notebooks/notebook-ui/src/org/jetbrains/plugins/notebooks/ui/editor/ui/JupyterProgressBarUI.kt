package org.jetbrains.plugins.notebooks.ui.editor.ui

import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.impl.text.TextEditorComponent
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.math.max

class JupyterProgressBarUI : DarculaProgressBarUI() {
  private fun getEditor(c: JComponent?): Editor? {
    var currentComponent = c
    while (currentComponent != null && currentComponent !is TextEditorComponent) {
      currentComponent = currentComponent.parent as? JComponent
    }

    if (currentComponent is TextEditorComponent) {
      return currentComponent.editor
    }
    return null
  }

  override fun getStartColor(c: JComponent): Color {
    val editor = getEditor(c)
    return editor?.notebookAppearance?.getCodeCellBackground(editor.colorsScheme) ?: Color.GRAY
  }

  override fun getFinishedColor(c: JComponent): Color {
    val editor = getEditor(c)
    return editor?.notebookAppearance?.getCodeCellBackground(editor.colorsScheme) ?: Color.GRAY
  }

  override fun getPreferredSize(c: JComponent?): Dimension {
    val editor = getEditor(c)
    if (editor != null) {
      val notebookAppearance = editor.notebookAppearance
      return Dimension(max(super.getPreferredSize(c)?.width ?: 0, editor.scrollingModel.visibleArea.width), notebookAppearance.PROGRESS_STATUS_HEIGHT)
    }
    return super.getPreferredSize(c)
  }
}