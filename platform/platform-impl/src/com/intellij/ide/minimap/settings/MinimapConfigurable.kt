// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.settings

import com.intellij.ide.minimap.utils.MiniMessagesBundle
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JPanel


class MinimapConfigurable : BoundConfigurable(MiniMessagesBundle.message("settings.name")) {

  companion object {
    const val ID = "com.intellij.minimap"
  }

  private val state = MinimapSettingsState() // todo remove
  private val fileTypes = mutableListOf<String>()

  private lateinit var fileTypeComboBox: ComboBox<FileType>

  override fun createPanel() = panel {
    MinimapSettings.getInstance().state.let {
      // todo remove except fileTypes
      state.enabled = it.enabled
      state.rightAligned = it.rightAligned
      state.width = it.width
      fileTypes.clear()
      fileTypes.addAll(it.fileTypes)
    }

    lateinit var enabled: JBCheckBox
    row {
      enabled = checkBox(MiniMessagesBundle.message("settings.enable"))
        .applyToComponent {
          toolTipText = MiniMessagesBundle.message("settings.enable.hint")
        }
        .bindSelected(state::enabled)
        .component
    }
    indent {
      buttonsGroup {
        row(MiniMessagesBundle.message("settings.alignment")) {
          radioButton(MiniMessagesBundle.message("settings.left"), false)
          radioButton(MiniMessagesBundle.message("settings.right"), true)
        }
      }.bind(state::rightAligned)
      row(MiniMessagesBundle.message("settings.file.types")) {
        val textFileTypes = (FileTypeManager.getInstance() as FileTypeManagerImpl).registeredFileTypes
          .filter { !it.isBinary && it.defaultExtension.isNotBlank() }.distinctBy { it.defaultExtension }
          .sortedBy { if (fileTypes.contains(it.defaultExtension)) 0 else 1 }

        fileTypeComboBox = comboBox(textFileTypes, FileTypeListCellRenderer())
          .applyToComponent {
            isSwingPopup = false
            addActionListener {
              val fileType = fileTypeComboBox.item ?: return@addActionListener
              if (!fileTypes.remove(fileType.defaultExtension)) {
                fileTypes.add(fileType.defaultExtension)
              }
              fileTypeComboBox.repaint()
            }
          }.component
      }
    }.enabledIf(enabled.selected)
  }

  override fun isModified(): Boolean {
    return super.isModified() || fileTypes != MinimapSettings.getInstance().state.fileTypes
  }

  override fun apply() {
    if (!isModified) {
      return
    }

    super.apply()
    state.fileTypes = fileTypes.toList()

    val settings = MinimapSettings.getInstance()
    val currentState = settings.state
    val needToRebuildUI = currentState.rightAligned != state.rightAligned ||
                          currentState.enabled != state.enabled ||
                          currentState.fileTypes != state.fileTypes

    settings.state = state.copy()
    settings.settingsChangeCallback.notify(if (needToRebuildUI)
                                             MinimapSettings.SettingsChangeType.WithUiRebuild
                                           else
                                             MinimapSettings.SettingsChangeType.Normal)
  }

  override fun reset() {
    super.reset()

    fileTypes.clear()
    fileTypes.addAll(MinimapSettings.getInstance().state.fileTypes)
    fileTypeComboBox.repaint()
  }

  private inner class FileTypeListCellRenderer : DefaultListCellRenderer() {
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
        text = fileTypes.joinToString(",")
        return container
      }
      checkBox.isVisible = true
      val fileType = value as? FileType ?: return container
      text = fileType.defaultExtension
      icon = fileType.icon
      checkBox.isSelected = fileTypes.contains(fileType.defaultExtension)
      return container
    }
  }
}