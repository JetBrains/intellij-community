// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.utils.ScientificUtils
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.enums.enumEntries

class ImageOperationsActionGroup : DefaultActionGroup(), CustomComponentAction, DumbAware {
  init {
    templatePresentation.isPerformGroup = true
  }

  private val customRenderer = listCellRenderer {
    if (index > 0 && value in FIRST_IN_GROUP_MODES) {
      separator { }
    }
    value.icon?.let { icon(it) }
    text(value.displayName)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val shouldShowTheGroup = Registry.`is`("ide.images.sci.mode.channels.operations")
    if (!shouldShowTheGroup) {
      e.presentation.isVisible = false
      return
    }
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = imageFile?.getUserData(ScientificUtils.SCIENTIFIC_MODE_KEY) != null
    e.presentation.putClientProperty(OPERATION_MODE_KEY, imageFile?.getUserData(CURRENT_OPERATION_MODE_KEY) ?: ImageOperationMode.ORIGINAL_IMAGE)
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    val modeSelector = component as? ComboBox<*> ?: return
    val fileMode = presentation.getClientProperty(OPERATION_MODE_KEY) ?: return
    selectSilently(modeSelector, fileMode)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val comboBoxModel = DefaultComboBoxModel(enumEntries<ImageOperationMode>().toTypedArray())
    return ComboBox(comboBoxModel).apply {
      selectedItem = ImageOperationMode.ORIGINAL_IMAGE
      isOpaque = false
      maximumRowCount = comboBoxModel.size
      renderer = customRenderer
      addActionListener { handleComboBoxSelection(this, place) }
    }
  }

  private fun handleComboBoxSelection(comboBox: ComboBox<ImageOperationMode>, place: String) {
    if (comboBox.getClientProperty(IS_SYNCING_SELECTION_KEY) == true) return
    val selectedMode = comboBox.selectedItem as? ImageOperationMode ?: return
    val dataContext = ActionToolbar.getDataContextFor(comboBox)
    val imageFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
    val currentMode = imageFile?.getUserData(CURRENT_OPERATION_MODE_KEY) ?: ImageOperationMode.ORIGINAL_IMAGE
    if (selectedMode == ImageOperationMode.CONFIGURE_BINARIZATION) {
      selectSilently(comboBox, currentMode)
      selectedMode.executeAction(dataContext, place)
    }
    else if (selectedMode.executeAction(dataContext, place)) {
      imageFile?.putUserData(CURRENT_OPERATION_MODE_KEY, selectedMode)
    }
    else {
      selectSilently(comboBox, currentMode)
    }
  }

  private fun selectSilently(comboBox: ComboBox<*>, mode: ImageOperationMode) {
    if (comboBox.selectedItem == mode) return
    comboBox.putClientProperty(IS_SYNCING_SELECTION_KEY, true)
    try {
      comboBox.selectedItem = mode
    }
    finally {
      comboBox.putClientProperty(IS_SYNCING_SELECTION_KEY, null)
    }
  }

  companion object {
    private val FIRST_IN_GROUP_MODES = setOf(
      ImageOperationMode.REVERSED_IMAGE,
      ImageOperationMode.CHANNEL_1,
      ImageOperationMode.CONFIGURE_BINARIZATION,
    )
    private const val IS_SYNCING_SELECTION_KEY = "ImageOperationsActionGroup.isSyncingSelection"
    private val OPERATION_MODE_KEY: Key<ImageOperationMode> = Key.create("ImageOperationsActionGroup.operationMode")
  }
}

enum class ImageOperationMode(@Nls val displayName: String, val icon: Icon? = null, private val actionId: String? = null) {
  ORIGINAL_IMAGE(ImagesBundle.message("image.color.mode.original.image"), actionId = "Images.RestoreOriginalImageAction"),
  REVERSED_IMAGE(ImagesBundle.message("image.color.mode.reversed.image"), actionId = "Images.ReverseChannelsOrderAction"),
  INVERTED_IMAGE(ImagesBundle.message("image.color.mode.inverted.image"), actionId = "Images.InvertChannelsAction"),
  GRAYSCALE_IMAGE(ImagesBundle.message("image.color.mode.grayscale.image"), actionId = "Images.GrayscaleImageAction"),
  BINARIZE_IMAGE(ImagesBundle.message("image.color.mode.binarize.image"), actionId = "Images.BinarizeImageAction"),
  CHANNEL_1(ImagesBundle.message("image.channels.mode.channel.1")),
  CHANNEL_2(ImagesBundle.message("image.channels.mode.channel.2")),
  CHANNEL_3(ImagesBundle.message("image.channels.mode.channel.3")),
  CONFIGURE_BINARIZATION(ImagesBundle.message("image.color.mode.configure.actions"), AllIcons.General.Settings, actionId = "Images.ConfigureActions");

  fun executeAction(actionEvent: AnActionEvent): Boolean {
    return executeAction(actionEvent.dataContext, actionEvent.place, actionEvent.inputEvent)
  }

  fun executeAction(dataContext: DataContext, place: String, inputEvent: InputEvent? = null): Boolean {
    val action = findAction() ?: return false
    val event = AnActionEvent.createEvent(
      action, dataContext, action.templatePresentation.clone(),
      place, ActionUiKind.NONE, inputEvent
    )
    ActionUtil.updateAction(action, event)
    if (!event.presentation.isEnabled) return false
    return ActionUtil.performAction(action, event).isPerformed
  }

  private fun findAction(): AnAction? = when (this) {
    CHANNEL_1, CHANNEL_2, CHANNEL_3 -> DisplaySingleChannelAction(ordinal - CHANNEL_1.ordinal)
    else -> actionId?.let { ActionManager.getInstance().getAction(it) }
  }
}