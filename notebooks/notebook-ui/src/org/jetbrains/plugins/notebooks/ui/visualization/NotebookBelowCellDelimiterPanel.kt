package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

class NotebookBelowCellDelimiterPanel(val editor: EditorImpl) : JPanel(GridLayout(1, 1)) {
  private var shouldUseCustomBackground = false
  private val labelFont = EditorUtil.getEditorFont()
  private val labelForeground = UIUtil.getLabelInfoForeground()

  private fun setupForDiffEditor() {
    preferredSize = Dimension(preferredSize.width, getJupyterCellSpacing(editor))
    val basicPanel = JPanel().apply {
      background = editor.colorsScheme.defaultBackground
    }

    val c = GridBagConstraints().apply {
      weightx = 1.0
      fill = GridBagConstraints.HORIZONTAL
      gridx = 0
      gridy = 0
    }

    add(basicPanel, c)
  }

  fun initialize(@Nls tooltipText: String?, executionCount: Int?, statusIcon: Icon, isExecutable: Boolean) {
    val notebookAppearance = editor.notebookAppearance
    background = editor.colorsScheme.defaultBackground
    preferredSize = Dimension(preferredSize.width, notebookAppearance.EXECUTION_TIME_HEIGHT)

    if (editor.editorKind == EditorKind.DIFF) {
      setupForDiffEditor()
      return
    }

    if (isExecutable) {
      val executionCountText = executionCount?.let { if (it > 0) "[$it]" else "[ ]" } ?: "[ ]"
      val executionLabel = JLabel(executionCountText)

      executionLabel.icon = statusIcon
      executionLabel.font = labelFont
      executionLabel.foreground = labelForeground

      if (!tooltipText.isNullOrEmpty()) {
        executionLabel.toolTipText = tooltipText
      }

      add(executionLabel, BorderLayout.WEST)
      shouldUseCustomBackground = true
    }
  }

  @Suppress("USELESS_ELVIS")
  override fun updateUI() {
    // This method is called within constructor of JPanel, at this time state is not yet initialized, reference is null.
    editor ?: return
    background = when(shouldUseCustomBackground) {
      true -> editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme)
      false -> editor.colorsScheme.defaultBackground
    }
    super.updateUI()
  }
}
