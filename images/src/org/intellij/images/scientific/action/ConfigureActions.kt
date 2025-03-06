// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.BinarizationThresholdConfig
import javax.swing.JOptionPane
import javax.swing.JTextField

class ConfigureActions : AnAction(ImagesBundle.message("image.color.mode.configure.actions"), null, AllIcons.General.Settings) {
  override fun actionPerformed(e: AnActionEvent) {
    openConfigurationDialog()
  }

  private fun openConfigurationDialog() {
    val thresholdConfig = ApplicationManager.getApplication().getService(BinarizationThresholdConfig::class.java) ?: return
    val currentThreshold = thresholdConfig.threshold
    val newThreshold = showThresholdDialog(currentThreshold)
    if (newThreshold != null) {
      thresholdConfig.threshold = newThreshold
    }
  }

  private fun showThresholdDialog(initialValue: Int): Int? {
    val inputField = JTextField(initialValue.toString())
    val optionPane = JOptionPane(inputField, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION)
    val dialog = optionPane.createDialog(null, ImagesBundle.message("image.binarize.dialog.title"))
    dialog.isAlwaysOnTop = true
    dialog.isVisible = true

    if (optionPane.value == JOptionPane.OK_OPTION) {
      val threshold = inputField.text.toIntOrNull()
      if (threshold != null && threshold in 0..255) {
        return threshold
      }
      else {
        JOptionPane.showMessageDialog(null, ImagesBundle.message("image.binarize.dialog.message"), ImagesBundle.message("image.binarize.dialog.invalid"), JOptionPane.ERROR_MESSAGE)
        return showThresholdDialog(initialValue)
      }
    }
    return null
  }
}