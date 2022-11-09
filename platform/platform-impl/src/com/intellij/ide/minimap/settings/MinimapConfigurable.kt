// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.settings

import com.intellij.ide.minimap.utils.MiniMessagesBundle
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import java.awt.Component
import javax.swing.*


class MinimapConfigurable : Configurable {

  companion object {
    const val ID = "com.intellij.minimap"
  }

  private val enabled = JBCheckBox(MiniMessagesBundle.message("settings.enable")).apply {
    toolTipText = MiniMessagesBundle.message("settings.enable.hint")
  }

  private val alignmentLeft = JRadioButton(MiniMessagesBundle.message("settings.left"))
  private val alignmentRight = JRadioButton(MiniMessagesBundle.message("settings.right"))

  private var lastState = MinimapSettings.getInstance().state.copy()

  private val fileTypeComboBox: ComboBox<FileType>

  init {
    val textFileTypes = (FileTypeManager.getInstance() as FileTypeManagerImpl).registeredFileTypes
      .filter { !it.isBinary && it.defaultExtension.isNotBlank() }.distinctBy { it.defaultExtension }
      .sortedBy { if (lastState.fileTypes.contains(it.defaultExtension)) 0 else 1 }.toTypedArray()

    fileTypeComboBox = ComboBox(textFileTypes).apply {
      isSwingPopup = false
    }

    fileTypeComboBox.addActionListener {
      val fileType = fileTypeComboBox.item ?: return@addActionListener
      if (lastState.fileTypes.contains(fileType.defaultExtension)) {
        lastState.fileTypes -= fileType.defaultExtension
      }
      else {
        lastState.fileTypes += fileType.defaultExtension
      }
    }

    fileTypeComboBox.renderer = FileTypeListCellRenderer()
  }

  override fun getDisplayName() = MiniMessagesBundle.message("settings.name")

  override fun createComponent() = panel {
    row { cell(enabled) }
    indent {
      buttonsGroup {
        row(MiniMessagesBundle.message("settings.alignment")) {
          cell(alignmentLeft)
          cell(alignmentRight)
        }
      }
      row(MiniMessagesBundle.message("settings.file.types")) {
        cell(fileTypeComboBox)
      }
    }
  }

  override fun isModified() = MinimapSettings.getInstance().state != getState()

  override fun apply() {
    if (!isModified) {
      return
    }

    val settings = MinimapSettings.getInstance()
    val currentState = getState()

    val needToRebuildUI = currentState.rightAligned != settings.state.rightAligned ||
                          currentState.enabled != settings.state.enabled ||
                          currentState.fileTypes != settings.state.fileTypes

    settings.state = currentState

    settings.settingsChangeCallback.notify(if (needToRebuildUI)
                                             MinimapSettings.SettingsChangeType.WithUiRebuild
                                           else
                                             MinimapSettings.SettingsChangeType.Normal)
  }

  override fun reset() {
    val state = MinimapSettings.getInstance().state

    enabled.isSelected = state.enabled
    alignmentRight.isSelected = state.rightAligned
    alignmentLeft.isSelected = !alignmentRight.isSelected
    fileTypeComboBox.repaint()

    lastState = state.copy()
  }

  override fun disposeUIResources() = Unit

  private fun getState() = MinimapSettingsState(enabled = enabled.isSelected,
                                                rightAligned = alignmentRight.isSelected,
                                                width = MinimapSettings.getInstance().state.width,
                                                fileTypes = lastState.fileTypes)

  inner class FileTypeListCellRenderer : DefaultListCellRenderer() {
    private val container = JPanel(null).apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
    }
    private val checkBox = JBCheckBox()

    init {
      container.add(checkBox)
      container.add(this)
    }

    override fun getListCellRendererComponent(list: JList<*>?,
                                              value: Any?,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

      if (index == -1) {
        checkBox.isVisible = false
        text = lastState.fileTypes.joinToString(",")
        return container
      }
      checkBox.isVisible = true
      val fileType = value as? FileType ?: return container
      text = fileType.defaultExtension
      icon = fileType.icon
      checkBox.isSelected = MinimapSettings.getInstance().state.fileTypes.contains(fileType.defaultExtension)
      return container
    }
  }
}