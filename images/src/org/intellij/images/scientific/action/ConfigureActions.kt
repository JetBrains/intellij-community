// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.utils.BinarizationThresholdConfig
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider

class ConfigureActions : AnAction(
  ImagesBundle.message("image.color.mode.configure.actions"),
  null,
  AllIcons.General.Settings
) {
  override fun actionPerformed(e: AnActionEvent) {
    openConfigurationDialog(e.project)
  }

  private fun openConfigurationDialog(project: Project?) {
    val thresholdConfig = BinarizationThresholdConfig.getInstance()
    val currentThreshold = thresholdConfig.threshold
    val dialog = ThresholdDialogWrapper(project, currentThreshold)
    if (dialog.showAndGet()) {
      val newThreshold = dialog.thresholdValue
      if (newThreshold != null) {
        thresholdConfig.threshold = newThreshold
      }
    }
  }

  private class ThresholdDialogWrapper(project: Project?, initialValue: Int) : DialogWrapper(project) {
    private var sliderValue: Int = initialValue.coerceIn(0, 255)
    var thresholdValue: Int? = null

    init {
      title = ImagesBundle.message("image.binarize.dialog.title")
      init()
    }

    override fun createCenterPanel(): JComponent {
      val slider = JSlider(0, 255, sliderValue).apply {
        majorTickSpacing = 50
        minorTickSpacing = 5
        paintTicks = true
        paintLabels = true
        value = sliderValue
        addChangeListener { sliderValue = this.value }
      }
      val valueLabel = JLabel("$sliderValue").apply {
        slider.addChangeListener { text = slider.value.toString() }
      }
      return JPanel(BorderLayout()).apply {
        add(slider, BorderLayout.CENTER)
        add(valueLabel, BorderLayout.SOUTH)
        preferredSize = Dimension(300, 120)
      }
    }

    override fun getPreferredFocusedComponent(): JComponent {
      return createCenterPanel()
    }

    override fun doOKAction() {
      thresholdValue = sliderValue
      super.doOKAction()
    }

    override fun getInitialSize(): Dimension {
      return Dimension(300, 120)
    }
  }
}