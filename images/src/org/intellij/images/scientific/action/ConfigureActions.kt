// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.BinarizationThresholdConfig
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider

class ConfigureActions : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    openConfigurationDialog(e.project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun openConfigurationDialog(project: Project?) {
    val thresholdConfig = BinarizationThresholdConfig.getInstance()
    val currentThreshold = thresholdConfig.threshold
    val dialog = ThresholdDialogWrapper(project, currentThreshold)
    if (dialog.showAndGet()) {
      thresholdConfig.threshold = dialog.threshold
    }
  }

  private class ThresholdDialogWrapper(project: Project?, initialValue: Int) : DialogWrapper(project) {
    private var thresholdValue: Int = initialValue.coerceIn(0, 255)
    val threshold: Int
      get() = thresholdValue

    init {
      title = ImagesBundle.message("image.binarize.dialog.title")
      isResizable = true
      init()
    }

    override fun createCenterPanel(): JComponent {
      val slider = JSlider(0, 255, thresholdValue).apply {
        majorTickSpacing = 50
        minorTickSpacing = 5
        paintTicks = true
        paintLabels = true
        value = thresholdValue
        addChangeListener { thresholdValue = this.value }
      }
      val valueLabel = JLabel(ImagesBundle.message("scientific.threshold.value", thresholdValue)).apply {
        slider.addChangeListener { text = ImagesBundle.message("scientific.threshold.value", slider.value) }
      }
      return JPanel(BorderLayout()).apply {
        add(slider, BorderLayout.CENTER)
        add(valueLabel, BorderLayout.SOUTH)
        border = JBUI.Borders.empty(10)
      }
    }

    override fun getPreferredFocusedComponent(): JComponent {
      return createCenterPanel()
    }
  }
}