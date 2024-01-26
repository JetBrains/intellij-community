package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.notebooks.ui.editor.ui.JupyterProgressBarUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.GridBagConstraints
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import kotlin.math.max

class NotebookBelowCellDelimiterPanel(val editor: EditorImpl) : JPanel(GridLayout(1, 1)) {
  private val steadyUI = JupyterProgressBarUI()
  private var shouldUseCustomBackground = false
  private val progress = object : JProgressBar(0, 100) {
    init {
      setStopped()
      setUI(steadyUI)
    }

    /** See [SteadyUIPanel] for explanation. */
    override fun updateUI() {
      setUI(steadyUI)
    }
  }

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

  fun initialize(@Nls executionTimeDetails: String?, hasProgressBar: Boolean) {
    val notebookAppearance = editor.notebookAppearance
    background = editor.colorsScheme.defaultBackground

    if (editor.editorKind == EditorKind.DIFF) {
      setupForDiffEditor()
      return
    }

    val customHeight = if (executionTimeDetails != null) notebookAppearance.EXECUTION_TIME_HEIGHT else notebookAppearance.SPACER_HEIGHT
    preferredSize = Dimension(preferredSize.width, customHeight)

    if (executionTimeDetails != null) {
      val label = JLabel(executionTimeDetails)
      // DS-4487
      val labelSize = minOf(JBFont.small().size, editor.fontSize - 2).toFloat()
      label.font = JBFont.label().deriveFont(max(1f, labelSize))
      label.foreground = UIUtil.getLabelInfoForeground()
      background = notebookAppearance.getCodeCellBackground(editor.colorsScheme)
      add(label, BorderLayout.WEST)
      shouldUseCustomBackground = true
    } else if (hasProgressBar) {
      background = notebookAppearance.getCodeCellBackground(editor.colorsScheme)
      border = BorderFactory.createEmptyBorder(notebookAppearance.SPACER_HEIGHT - notebookAppearance.PROGRESS_STATUS_HEIGHT, 0, 0, 7)
      add(progress.apply {
        setUI(steadyUI)
      })
      setProgressVisibility(false)
      shouldUseCustomBackground = true
    }
  }

  override fun updateUI() {
    // This method is called within constructor of JPanel, at this time state is not yet initialised, reference is null.
    if (editor != null) {
      background =
        if (shouldUseCustomBackground)
          editor.notebookAppearance.getCodeCellBackground(editor.colorsScheme)
        else editor.colorsScheme.defaultBackground
    }
    super.updateUI()
  }

  fun setProgressVisibility(showProgressBar: Boolean) {
    EDT.assertIsEdt()
    progress.isVisible = when {
      showProgressBar -> {
        progress.setRunning()
        true
      }
      else -> {
        progress.setStopped()
        false
      }
    }
  }
}

private fun JProgressBar.setRunning() {
  isIndeterminate = true
}

private fun JProgressBar.setStopped() {
  isIndeterminate = false
  value = maximum
}