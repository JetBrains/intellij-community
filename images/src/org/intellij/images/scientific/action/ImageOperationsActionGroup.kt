// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.util.ui.JBUI
import org.intellij.images.ImagesBundle
import org.intellij.images.scientific.utils.ScientificUtils
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class ImageOperationsActionGroup : DefaultActionGroup(), CustomComponentAction, DumbAware {
  private var selectedMode: ImageOperationMode = ImageOperationMode.ORIGINAL_IMAGE
  private var imageOptions: ComboBox<ImageOperationMode>? = null

  private val customRenderer = object : ListCellRenderer<Any?> {
    private val separator = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
    private val itemComponent = JLabel()
    private val panel = JPanel(BorderLayout())

    init {
      panel.isOpaque = false
      panel.border = null
      itemComponent.isOpaque = false
      panel.add(separator, BorderLayout.NORTH)
      panel.add(itemComponent, BorderLayout.CENTER)
      templatePresentation.apply {
        isPerformGroup = true
      }
    }

    override fun getListCellRendererComponent(
      list: JList<out Any>?, value: Any?,
      index: Int, isSelected: Boolean, cellHasFocus: Boolean
    ): Component {
      val mode = value as? ImageOperationMode
      itemComponent.text = mode?.displayName
      itemComponent.icon = mode?.icon
      itemComponent.border = null

      if (isSelected) {
        itemComponent.foreground = list?.selectionForeground
        itemComponent.background = list?.selectionBackground
        itemComponent.isOpaque = true
      } else {
        itemComponent.foreground = list?.foreground
        itemComponent.background = list?.background
        itemComponent.isOpaque = false
      }
      separator.isVisible = shouldShowSeparator(list, index)
      return panel
    }

    private fun shouldShowSeparator(list: JList<out Any>?, index: Int): Boolean {
      if (index <= 0) return false
      val prevItem = list?.model?.getElementAt(index - 1) as? ImageOperationMode
      return prevItem == ImageOperationMode.ORIGINAL_IMAGE ||
             prevItem == ImageOperationMode.BINARIZE_IMAGE ||
             prevItem == ImageOperationMode.CHANNEL_3
    }
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
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    selectedMode = ImageOperationMode.ORIGINAL_IMAGE
    val comboBoxModel = DefaultComboBoxModel(enumValues<ImageOperationMode>())
    val comboBox = ComboBox(comboBoxModel).apply {
      selectedItem = selectedMode
      isOpaque = false
      maximumRowCount = comboBoxModel.size
      renderer = customRenderer
      addActionListener { handleComboBoxSelection(this) }
    }
    imageOptions = comboBox
    return JPanel(FlowLayout(FlowLayout.CENTER, 0, 0)).apply {
      isOpaque = false
      border = null
      add(comboBox)
    }
  }

  fun updateSelectedMode(mode: ImageOperationMode) {
    selectedMode = mode
    imageOptions?.selectedItem = mode
  }

  private fun handleComboBoxSelection(comboBox: ComboBox<ImageOperationMode>) {
    val selectedItem = comboBox.selectedItem as? ImageOperationMode ?: return
    if (selectedItem == ImageOperationMode.CONFIGURE_BINARIZATION) {
      comboBox.selectedItem = selectedMode
      selectedItem.executeAction()
    } else {
      selectedMode = selectedItem
      selectedMode.executeAction()
    }
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
  CONFIGURE_BINARIZATION(ImagesBundle.message("image.color.mode.configure.actions"), AllIcons.General.Settings);

  fun executeAction(actionEvent: AnActionEvent? = null) {
    val actionManager = ActionManager.getInstance()

    fun run(action: AnAction) {
      if (actionEvent != null) {
        val presentationCopy = action.templatePresentation.clone()
        val event = AnActionEvent.createEvent(
          action, actionEvent.dataContext, presentationCopy,
          actionEvent.place, ActionUiKind.NONE, actionEvent.inputEvent
        )
        ActionUtil.performAction(action, event)
      }
      else {
        actionManager.tryToExecute(action, null, null, null, true)
      }
    }
    when {
      actionId != null -> {
        val action = actionManager.getAction(actionId) ?: return
        run(action)
      }
      this in listOf(CHANNEL_1, CHANNEL_2, CHANNEL_3) -> {
        val channelIndex = ordinal - CHANNEL_1.ordinal
        run(DisplaySingleChannelAction(channelIndex))
      }
      this == CONFIGURE_BINARIZATION -> {
        val action = actionManager.getAction("Images.ConfigureActions") ?: return
        run(action)
      }
    }
  }
}