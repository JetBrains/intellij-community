// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.utils.ScientificUtils
import org.jetbrains.annotations.Nls
import java.awt.FlowLayout
import javax.swing.*

class ImageOperationsActionGroup : DefaultActionGroup(), CustomComponentAction, DumbAware {
  private var selectedMode: String = ORIGINAL_IMAGE

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

    val comboBoxModel = DefaultComboBoxModel<String>().apply {
      addElement(ORIGINAL_IMAGE)
      addElement(INVERTED_IMAGE)
      addElement(GRAYSCALE_IMAGE)
      addElement(BINARIZE_IMAGE)
      addElement(CONFIGURE_ACTIONS)
    }

    val comboBox = ComboBox(comboBoxModel).apply {
      selectedItem = selectedMode
      isOpaque = false
      renderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
          list: JList<out Any>?,
          value: Any?,
          index: Int,
          isSelected: Boolean,
          cellHasFocus: Boolean,
        ): JComponent {
          val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
          if (index != -1 && value == CONFIGURE_ACTIONS) {
            return JSeparator().apply {
              icon = if (value == CONFIGURE_ACTIONS) AllIcons.General.Settings else null
              layout = BoxLayout(this, BoxLayout.Y_AXIS)
              isOpaque = false
              background = JBColor(Gray._200, Gray._100)
              add(component)
            }
          }

          return component as JComponent
        }
      }
      addActionListener {
        val selectedItem = selectedItem as String
        if (selectedItem == CONFIGURE_ACTIONS) {
          this.selectedItem = selectedMode
          triggerModeAction(CONFIGURE_ACTIONS)
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
    actionGroup.add(ConfigureActions())
    return actionGroup
  }

  private fun triggerModeAction(mode: String) {
    val actionManager = ActionManager.getInstance()
    when (mode) {
      ORIGINAL_IMAGE -> actionManager.tryToExecute(RestoreOriginalImageAction(), null, null, null, true)
      INVERTED_IMAGE -> actionManager.tryToExecute(InvertChannelsAction(), null, null, null, true)
      GRAYSCALE_IMAGE -> actionManager.tryToExecute(GrayscaleImageAction(), null, null, null, true)
      BINARIZE_IMAGE -> actionManager.tryToExecute(BinarizeImageAction(), null, null, null, true)
      CONFIGURE_ACTIONS -> actionManager.tryToExecute(ConfigureActions(), null, null, null, true)
    }
  }

  companion object {
    @Nls
    private val ORIGINAL_IMAGE: String = ImagesBundle.message("image.color.mode.original.image")

    @Nls
    private val INVERTED_IMAGE: String = ImagesBundle.message("image.color.mode.inverted.image")

    @Nls
    private val GRAYSCALE_IMAGE: String = ImagesBundle.message("image.color.mode.grayscale.image")

    @Nls
    private val BINARIZE_IMAGE: String = ImagesBundle.message("image.color.mode.binarize.image")

    @Nls
    private val CONFIGURE_ACTIONS: String = ImagesBundle.message("image.color.mode.configure.actions")
  }
}