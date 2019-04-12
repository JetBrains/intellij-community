// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UINumericRange
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsState
import com.intellij.openapi.application.ApplicationBundle.message
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ListCellRendererWrapper
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import javax.swing.*

class EditorTabsConfigurable : EditorOptionsProvider {
  companion object {
    private const val LEFT = "Left"
    private const val RIGHT = "Right"
    private const val NONE = "None"

    private val EDITOR_TABS_RANGE = UINumericRange(10, 1, Math.max(10, Registry.intValue("ide.max.editor.tabs", 100)))
  }

  private val myEditorTabPlacement: JComboBox<Int> = ComboBox(
    arrayOf(SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT, UISettings.TABS_NONE)
  )
  private val myScrollTabLayoutInEditorCheckBox = JCheckBox(message("checkbox.editor.tabs.in.single.row"))
  private val myHideTabsCheckbox = JCheckBox(message("checkbox.editor.scroll.if.need"))
  private lateinit var myCloseButtonPlacementRow: Row
  private val panel = doCreateComponent()

  init {
    myEditorTabPlacement.renderer = MyTabsPlacementComboBoxRenderer()
    myEditorTabPlacement.addItemListener { revalidateSingleRowCheckbox() }

    revalidateSingleRowCheckbox()
    myScrollTabLayoutInEditorCheckBox.addChangeListener { myHideTabsCheckbox.isEnabled = myScrollTabLayoutInEditorCheckBox.isSelected }
  }

  private fun revalidateSingleRowCheckbox() {
    val i = (myEditorTabPlacement.selectedItem as Int).toInt()

    val none = i == UISettings.TABS_NONE
    myHideTabsCheckbox.isEnabled = !none && myScrollTabLayoutInEditorCheckBox.isSelected
    myScrollTabLayoutInEditorCheckBox.isEnabled = !none
    myCloseButtonPlacementRow.enabled = !none

    if (SwingConstants.TOP == i) {
      myScrollTabLayoutInEditorCheckBox.isEnabled = true
    }
    else {
      myScrollTabLayoutInEditorCheckBox.isSelected = true
      myScrollTabLayoutInEditorCheckBox.isEnabled = false
    }
  }

  override fun getDisplayName() = "Editor Tabs (New)"

  override fun getHelpTopic() = "reference.settingsdialog.IDE.editor.tabs"

  override fun createComponent(): JComponent {
    return panel
  }

  private fun doCreateComponent(): DialogPanel {
    val uiSettings = UISettings.instance.state

    return panel {
      titledRow(message("group.tab.appearance")) {
        row {
          cell {
            Label(message("combobox.editor.tab.placement"))()
            myEditorTabPlacement()
          }
        }
        row {
          myScrollTabLayoutInEditorCheckBox()
          row {
            myHideTabsCheckbox()
          }
        }
        row {
          checkBox(
            message("checkbox.show.file.extension.in.editor.tabs"),
            { !uiSettings.hideKnownExtensionInTabs },
            { uiSettings.hideKnownExtensionInTabs = !it }
          ).enableIfTabsVisible()
        }
        row { checkBox(message("checkbox.show.directory.for.non.unique.files"), uiSettings::showDirectoryForNonUniqueFilenames).enableIfTabsVisible() }
        row { checkBox(message("checkbox.mark.modified.tabs.with.asterisk"), uiSettings::markModifiedTabsWithAsterisk).enableIfTabsVisible() }
        row { checkBox(message("checkbox.show.tabs.tooltips"), uiSettings::showTabsTooltips).enableIfTabsVisible() }
        myCloseButtonPlacementRow = row {
          cell {
            Label(message("tabs.close.button.placement"))()
            comboBox(
              DefaultComboBoxModel<String>(arrayOf(LEFT, RIGHT, NONE)),
              { getCloseButtonPlacement(uiSettings) },
              {
                uiSettings.showCloseButton = it !== NONE
                if (it !== NONE) {
                  uiSettings.closeTabButtonOnTheRight = it === RIGHT
                }
              }
            )
          }
        }
      }
      titledRow(message("group.tab.closing.policy")) {
        row {
          cell {
            Label(message("editbox.tab.limit"))()
            intTextField(uiSettings::editorTabLimit, 4, EDITOR_TABS_RANGE)
          }
        }
        row {
          label(message("label.when.number.of.opened.editors.exceeds.tab.limit"))
          buttonGroup {
            row { radioButton(message("radio.close.non.modified.files.first"), uiSettings::closeNonModifiedFilesFirst) }
            row { radioButton(message("radio.close.less.frequently.used.files")) }
              .largeGapAfter()
          }
        }
        row {
          label(message("label.when.closing.active.editor"))
          buttonGroup {
            row { radioButton(message("radio.activate.left.neighbouring.tab")) }
            row { radioButton(message("radio.activate.right.neighbouring.tab"), uiSettings::activeRightEditorOnClose) }
            row { radioButton(message("radio.activate.most.recently.opened.tab"), uiSettings::activeMruEditorOnClose) }
              .largeGapAfter()
          }
        }
        row {
          checkBox(message("checkbox.smart.tab.reuse"),
                   uiSettings::reuseNotModifiedTabs,
                   comment = message("checkbox.smart.tab.reuse.inline.help"))
        }
      }
    }
  }

  private fun <T : JComponent> CellBuilder<T>.enableIfTabsVisible() {
    enableIfSelected(myEditorTabPlacement) { it != UISettings.TABS_NONE }
  }

  override fun reset() {
    panel.reset()

    val uiSettings = UISettings.instance.state

    myScrollTabLayoutInEditorCheckBox.isSelected = uiSettings.scrollTabLayoutInEditor
    myHideTabsCheckbox.isEnabled = myScrollTabLayoutInEditorCheckBox.isSelected
    myHideTabsCheckbox.isSelected = uiSettings.hideTabsIfNeed
    myEditorTabPlacement.selectedItem = uiSettings.editorTabPlacement
  }

  private fun getCloseButtonPlacement(uiSettings: UISettingsState): String {
    val placement: String
    if (!uiSettings.showCloseButton) {
      placement = NONE
    }
    else {
      placement = if (java.lang.Boolean.getBoolean("closeTabButtonOnTheLeft") || !uiSettings.closeTabButtonOnTheRight) LEFT else RIGHT
    }
    return placement
  }

  override fun apply() {
    var uiSettingsChanged = panel.isModified()
    panel.apply()
    val settingsManager = UISettings.instance
    val uiSettings = settingsManager.state

    if (isModified(myScrollTabLayoutInEditorCheckBox, uiSettings.scrollTabLayoutInEditor)) uiSettingsChanged = true
    uiSettings.scrollTabLayoutInEditor = myScrollTabLayoutInEditorCheckBox.isSelected

    if (isModified(myHideTabsCheckbox, uiSettings.hideTabsIfNeed)) uiSettingsChanged = true
    uiSettings.hideTabsIfNeed = myHideTabsCheckbox.isSelected

    val tabPlacement = (myEditorTabPlacement.selectedItem as Int).toInt()
    if (uiSettings.editorTabPlacement != tabPlacement) uiSettingsChanged = true
    uiSettings.editorTabPlacement = tabPlacement

    if (uiSettingsChanged) {
      settingsManager.fireUISettingsChanged()
    }
  }

  override fun isModified(): Boolean {
    if (panel.isModified()) return true

    val uiSettings = UISettings.instance.state
    val tabPlacement = (myEditorTabPlacement.selectedItem as Int).toInt()
    var  isModified = tabPlacement != uiSettings.editorTabPlacement

    isModified = isModified or (myScrollTabLayoutInEditorCheckBox.isSelected != uiSettings.scrollTabLayoutInEditor)
    isModified = isModified or (myHideTabsCheckbox.isSelected != uiSettings.hideTabsIfNeed)

    return isModified
  }

  private class MyTabsPlacementComboBoxRenderer internal constructor() : ListCellRendererWrapper<Int>() {

    override fun customize(list: JList<*>, value: Int, index: Int, selected: Boolean, hasFocus: Boolean) {
      val text = when (value) {
        UISettings.TABS_NONE -> message("combobox.tab.placement.none")
        SwingConstants.TOP -> message("combobox.tab.placement.top")
        SwingConstants.LEFT -> message("combobox.tab.placement.left")
        SwingConstants.BOTTOM -> message("combobox.tab.placement.bottom")
        SwingConstants.RIGHT -> message("combobox.tab.placement.right")
        else -> throw IllegalArgumentException("unknown tabPlacement: $value")
      }
      setText(text)
    }
  }

  override fun getId() = "editor.preferences.tabs"
}