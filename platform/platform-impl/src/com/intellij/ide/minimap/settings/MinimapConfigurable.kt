// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.settings

import com.intellij.ide.minimap.utils.MiniMessagesBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JRadioButton

class MinimapConfigurable : Configurable {

  companion object {
    const val ID = "com.intellij.minimap"
  }

  private val enabled = JBCheckBox(MiniMessagesBundle.message("settings.enable")).apply {
    toolTipText = MiniMessagesBundle.message("settings.enable.hint")
  }
  private val filterComboBox = ComboBox(FilterType.values()).apply {
    toolTipText = MiniMessagesBundle.message("settings.filter.hint")
  }
  private val alignmentLeft = JRadioButton(MiniMessagesBundle.message("settings.left"))
  private val alignmentRight = JRadioButton(MiniMessagesBundle.message("settings.right"))
  private val enableForAll = JRadioButton(MiniMessagesBundle.message("settings.enable.all"))
  private val enableForZeppelin = JRadioButton(MiniMessagesBundle.message("settings.enable.zeppelin"))
  private val fileTypes = JBTextField(20)
  private var lastState: MinimapSettingsState? = null

  override fun getDisplayName() = MiniMessagesBundle.message("settings.name")

  override fun createComponent() = panel {
    row { cell(enabled) }
    indent {
      buttonsGroup {
        row(MiniMessagesBundle.message("settings.enable.scope")) {
          cell(enableForAll)
          cell(enableForZeppelin)
        }
      }
      buttonsGroup {
        row(MiniMessagesBundle.message("settings.alignment")) {
          cell(alignmentLeft)
          cell(alignmentRight)
        }
      }
    }
  }

  override fun isModified() = lastState != getState()

  override fun apply() {
    if (!isModified) {
      return
    }

    val state = getState()

    val needToReconstructUi = state.rightAligned != lastState?.rightAligned ||
                              state.enabled != lastState?.enabled ||
                              state.filterType != lastState?.filterType ||
                              state.fileTypes != lastState?.fileTypes

    val settings = MinimapSettings.getInstance()
    settings.state = state

    settings.settingsChangeCallback.notify(if (needToReconstructUi)
                                             MinimapSettings.SettingsChangeType.WithUiRebuild
                                           else
                                             MinimapSettings.SettingsChangeType.Normal)
  }

  override fun reset() {
    val state = MinimapSettings.getInstance().state

    filterComboBox.item = state.filterType
    enabled.isSelected = state.enabled
    alignmentRight.isSelected = state.rightAligned
    alignmentLeft.isSelected = !alignmentRight.isSelected
    fileTypes.text = state.fileTypes.joinToString(";")

    enableForZeppelin.isSelected = state.fileTypes.isNotEmpty()
    enableForAll.isSelected = !enableForZeppelin.isSelected

    lastState = state
  }

  override fun disposeUIResources() = Unit

  private fun getState() = MinimapSettingsState(filterType = filterComboBox.item,
                                                enabled = enabled.isSelected,
                                                rightAligned = alignmentRight.isSelected,
                                                fileTypes = if (enableForZeppelin.isSelected) listOf("zpln") else emptyList())
}