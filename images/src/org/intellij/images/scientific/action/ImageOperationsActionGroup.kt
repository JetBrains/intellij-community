// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.registry.Registry
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.BinarizationThresholdConfig
import org.intellij.images.scientific.ScientificUtils
import java.awt.FlowLayout
import javax.swing.*

class ImageOperationsActionGroup : DefaultActionGroup(), CustomComponentAction, DumbAware {
  private var selectedMode: String = ORIGINAL_IMAGE
  private val availableModes = listOf(ORIGINAL_IMAGE, INVERTED_IMAGE, GRAYSCALE_IMAGE, BINARIZE_IMAGE)
  private val CONFIGURE_ACTIONS = ImagesBundle.message("image.color.mode.configure.actions")

  init {
    templatePresentation.apply {
      isPerformGroup = true
      isPopup = true
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val component = e.inputEvent?.source as? JComponent ?: return
    JBPopupFactory.getInstance().createActionGroupPopup(
      null,
      createPopupActionGroup(),
      e.dataContext,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      true
    ).showUnderneathOf(component)
  }

  override fun update(e: AnActionEvent) {
    val shouldShowTheGroup = Registry.`is`("ide.images.sci.mode.channels.operations")
    if (!shouldShowTheGroup) {
      e.presentation.isVisible = false
      return
    }
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = imageFile?.getUserData(ScientificUtils.SCIENTIFIC_MODE_KEY) != null
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    selectedMode = ORIGINAL_IMAGE
    val comboBox = ComboBox(DefaultComboBoxModel((availableModes + CONFIGURE_ACTIONS).toTypedArray())).apply {
      selectedItem = selectedMode
      isOpaque = false
      addActionListener {
        val selectedItem = selectedItem as String
        if (selectedItem == CONFIGURE_ACTIONS) {
          this.selectedItem = selectedMode
          openConfigurationDialog()
        }
        else {
          selectedMode = selectedItem
          triggerModeAction(selectedMode)
        }
      }
    }
    return JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
      isOpaque = false
      border = null
      add(comboBox)
    }
  }

  private fun createPopupActionGroup(): DefaultActionGroup {
    val actionGroup = DefaultActionGroup()
    actionGroup.add(RestoreOriginalImageAction())
    actionGroup.add(InvertChannelsAction())
    actionGroup.add(GrayscaleImageAction())
    actionGroup.add(BinarizeImageAction())
    actionGroup.addSeparator()
    actionGroup.add(object : AnAction(CONFIGURE_ACTIONS) {
      override fun actionPerformed(e: AnActionEvent) {
        openConfigurationDialog()
      }


      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
      }
    })

    return actionGroup
  }

  private fun triggerModeAction(mode: String) {
    val actionManager = ActionManager.getInstance()
    when (mode) {
      ORIGINAL_IMAGE -> actionManager.tryToExecute(RestoreOriginalImageAction(), null, null, null, true)
      INVERTED_IMAGE -> actionManager.tryToExecute(InvertChannelsAction(), null, null, null, true)
      GRAYSCALE_IMAGE -> actionManager.tryToExecute(GrayscaleImageAction(), null, null, null, true)
      BINARIZE_IMAGE -> actionManager.tryToExecute(BinarizeImageAction(), null, null, null, true)
    }
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
    val optionPane = JOptionPane(
      inputField,
      JOptionPane.PLAIN_MESSAGE,
      JOptionPane.OK_CANCEL_OPTION
    )
    val dialog = optionPane.createDialog(null, ImagesBundle.message("image.binarize.dialog.title"))
    dialog.isAlwaysOnTop = true
    dialog.isVisible = true

    if (optionPane.value == JOptionPane.OK_OPTION) {
      val threshold = inputField.text.toIntOrNull()
      if (threshold != null && threshold in 0..255) {
        return threshold
      }
      else {
        JOptionPane.showMessageDialog(
          null,
          ImagesBundle.message("image.binarize.dialog.message"),
          ImagesBundle.message("image.binarize.dialog.invalid"),
          JOptionPane.ERROR_MESSAGE
        )
        return showThresholdDialog(initialValue)
      }
    }
    return null
  }

  companion object {
    private val ORIGINAL_IMAGE: String = ImagesBundle.message("image.color.mode.original.image")
    private val INVERTED_IMAGE: String = ImagesBundle.message("image.color.mode.inverted.image")
    private val GRAYSCALE_IMAGE: String = ImagesBundle.message("image.color.mode.grayscale.image")
    private val BINARIZE_IMAGE: String = ImagesBundle.message("image.color.mode.binarize.image")
  }
}