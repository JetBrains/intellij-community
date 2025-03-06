// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.BinarizationThresholdConfig
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class ConfigureActions : AnAction(
  ImagesBundle.message("image.color.mode.configure.actions"),
  null,
  AllIcons.General.Settings
) {
  override fun actionPerformed(e: AnActionEvent) {
    openConfigurationDialog(e.project)
  }

  private fun openConfigurationDialog(project: Project?) {
    val thresholdConfig = ApplicationManager.getApplication().getService(BinarizationThresholdConfig::class.java) ?: return
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
    private val inputField = JTextField(initialValue.toString()).apply {
      preferredSize = Dimension(150, 10)
      maximumSize = Dimension(150, 10)
      minimumSize = Dimension(150, 10)
    }

    var thresholdValue: Int? = null

    init {
      title = ImagesBundle.message("image.binarize.dialog.title")
      init()
    }

    override fun createCenterPanel(): JComponent {
      val panel = JPanel(BorderLayout())
      panel.add(inputField, BorderLayout.CENTER)
      return panel
    }

    override fun getPreferredFocusedComponent(): JComponent {
      return inputField
    }

    override fun doOKAction() {
      val value = inputField.text.toIntOrNull()
      if (value != null && value in 0..255) {
        thresholdValue = value
        super.doOKAction()
      }
      else {
        setErrorText(ImagesBundle.message("image.binarize.dialog.invalid"))
      }
    }

    override fun getInitialSize(): Dimension {
      return Dimension(300, 120)
    }
  }
}