// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.settings

import com.intellij.ide.minimap.utils.MiniMessagesBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.scale.JBUIScale
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

class MinimapConfigurable : Configurable {

  companion object {
    const val ID = "com.intellij.minimap"
  }

  private val enabled = JBCheckBox(MiniMessagesBundle.message("settings.enable"))
  private val resizable = JBCheckBox(MiniMessagesBundle.message("settings.resize"))
  private val widthField = JSpinner(SpinnerNumberModel().apply { minimum = 10 })
  private val filterComboBox = ComboBox(FilterType.values())
  private val alignmentLeft = JRadioButton(MiniMessagesBundle.message("settings.left"))
  private val alignmentRight = JRadioButton(MiniMessagesBundle.message("settings.right"))
  private val fileTypes = JBTextField(20)
  private var lastState: MinimapSettingsState? = null

  override fun getDisplayName() = MiniMessagesBundle.message("settings.name")

  override fun createComponent(): JComponent {
    ButtonGroup().apply {
      add(alignmentLeft)
      add(alignmentRight)
    }

    val alignmentPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      add(alignmentLeft)
      add(Box.createRigidArea(Dimension(JBUIScale.scale(5), 0)))
      add(alignmentRight)
    }

    enabled.toolTipText = MiniMessagesBundle.message("settings.enable.hint")
    resizable.toolTipText = MiniMessagesBundle.message("settings.resize.hint")
    filterComboBox.toolTipText = MiniMessagesBundle.message("settings.filter.hint")

    return JPanel(BorderLayout()).apply {

      val label = JLabel(MiniMessagesBundle.message("settings.description"))
      add(label, BorderLayout.NORTH)

      val panel = JPanel(GridBagLayout()).apply {

        val c = GridBagConstraints().apply {
          anchor = GridBagConstraints.WEST
        }

        add(resizable, c.apply { gridx = 0; gridy = 0 })

        add(JLabel(MiniMessagesBundle.message("settings.width")), c.apply { gridx = 0; gridy = 1; gridwidth = 1 })
        add(widthField, c.apply { gridx = 1; gridy = 1 })

        // Filtering is currently unavailable.
        //add(JLabel(MiniMessagesBundle.message("config.filter")), c.apply { gridx = 0; gridy = 2 })
        //add(filterComboBox, c.apply { gridx = 1; gridy = 2 })

        add(JLabel(MiniMessagesBundle.message("settings.alignment")), c.apply { gridx = 0; gridy = 3 })
        add(alignmentPanel, c.apply { gridx = 1; gridy = 3 })

        add(enabled, c.apply { gridx = 0; gridy = 4 })
        add(fileTypes, c.apply { gridx = 1; gridy = 4 })
      }

      val innerPanel = JPanel(BorderLayout()).apply {
        add(panel, BorderLayout.NORTH)
      }

      add(innerPanel, BorderLayout.WEST)
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
                              state.filterType != lastState?.filterType

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
    resizable.isSelected = state.resizable
    widthField.value = state.width
    alignmentRight.isSelected = state.rightAligned
    alignmentLeft.isSelected = !alignmentRight.isSelected
    fileTypes.text = state.fileTypes.joinToString(";")

    lastState = state
  }

  override fun disposeUIResources() = Unit

  private fun getState() = MinimapSettingsState(filterType = filterComboBox.item,
                                                enabled = enabled.isSelected,
                                                resizable = resizable.isSelected,
                                                width = widthField.value as Int,
                                                rightAligned = alignmentRight.isSelected,
                                                fileTypes = fileTypes.text.split(";").filter { it.isNotBlank() })
}