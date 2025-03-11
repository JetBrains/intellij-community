// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.registry.Registry
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.ScientificUtils
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

class ImageOperationsActionGroup : DefaultActionGroup(), CustomComponentAction, DumbAware {
  private var selectedMode: String = ORIGINAL_IMAGE
  private val availableModes = listOf(ORIGINAL_IMAGE, INVERTED_IMAGE, GRAYSCALE_IMAGE)

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
    val comboBox = ComboBox(DefaultComboBoxModel(availableModes.toTypedArray())).apply {
      selectedItem = selectedMode
      isOpaque = false
      addActionListener {
        selectedMode = selectedItem as String
        triggerModeAction(selectedMode)
      }
    }
    return JPanel(BorderLayout()).apply {
      isOpaque = false
      border = null
      add(comboBox, BorderLayout.CENTER)
    }
  }

  private fun createPopupActionGroup(): DefaultActionGroup {
    val actionGroup = DefaultActionGroup()
    actionGroup.add(RestoreOriginalImageAction())
    actionGroup.add(InvertChannelsAction())
    actionGroup.add(GrayscaleImageAction())
    return actionGroup
  }

  private fun triggerModeAction(mode: String) {
    val actionManager = ActionManager.getInstance()
    when (mode) {
      ORIGINAL_IMAGE -> actionManager.tryToExecute(RestoreOriginalImageAction(), null, null, null, true)
      INVERTED_IMAGE -> actionManager.tryToExecute(InvertChannelsAction(), null, null, null, true)
      GRAYSCALE_IMAGE -> actionManager.tryToExecute(GrayscaleImageAction(), null, null, null, true)
    }
  }

  companion object {
    private val ORIGINAL_IMAGE: String = ImagesBundle.message("image.color.mode.original.image")
    private val INVERTED_IMAGE: String = ImagesBundle.message("image.color.mode.inverted.image")
    private val GRAYSCALE_IMAGE: String = ImagesBundle.message("image.color.mode.grayscale.image")
  }
}