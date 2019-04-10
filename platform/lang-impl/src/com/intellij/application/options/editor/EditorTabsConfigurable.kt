// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UINumericRange
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsState
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationBundle.message
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ListCellRendererWrapper
import com.intellij.ui.layout.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

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
  private val myShowKnownExtensions = JCheckBox(message("checkbox.show.file.extension.in.editor.tabs"))
  private val myShowDirectoryInTabCheckBox = JCheckBox(message("checkbox.show.directory.for.non.unique.files"))
  private val myCbModifiedTabsMarkedWithAsterisk = JCheckBox(message("checkbox.mark.modified.tabs.with.asterisk"))
  private val myShowTabsTooltipsCheckBox = JCheckBox(message("checkbox.show.tabs.tooltips"))
  private val myCloseButtonPlacement = ComboBox<String>(arrayOf(LEFT, RIGHT, NONE))
  private val myEditorTabLimitField = JTextField(4)
  private val myCloseNonModifiedFilesFirstRadio = JRadioButton(message("radio.close.non.modified.files.first"))
  private val myCloseLRUFilesRadio = JRadioButton(message("radio.close.less.frequently.used.files"))
  private val myActivateLeftEditorOnCloseRadio = JRadioButton(message("radio.activate.left.neighbouring.tab"))
  private val myActivateRightNeighbouringTabRadioButton = JRadioButton(message("radio.activate.right.neighbouring.tab"))
  private val myActivateMRUEditorOnCloseRadio = JRadioButton(message("radio.activate.most.recently.opened.tab"))
  private val myReuseNotModifiedTabsCheckBox = JCheckBox(message("checkbox.smart.tab.reuse"))
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
    myShowKnownExtensions.isEnabled = !none
    myHideTabsCheckbox.isEnabled = !none && myScrollTabLayoutInEditorCheckBox.isSelected
    myScrollTabLayoutInEditorCheckBox.isEnabled = !none
    myCbModifiedTabsMarkedWithAsterisk.isEnabled = !none
    myShowTabsTooltipsCheckBox.isEnabled = !none
    myCloseButtonPlacementRow.enabled = !none
    myShowDirectoryInTabCheckBox.isEnabled = !none

    if (SwingConstants.TOP == i) {
      myScrollTabLayoutInEditorCheckBox.isEnabled = true
    }
    else {
      myScrollTabLayoutInEditorCheckBox.isSelected = true
      myScrollTabLayoutInEditorCheckBox.isEnabled = false
    }
  }

  override fun getDisplayName() = "Editor Tabs (New)"

  override fun getHelpTopic()= "reference.settingsdialog.IDE.editor.tabs"

  override fun createComponent(): JComponent {
    return panel
  }

  private fun doCreateComponent(): DialogPanel {
    return panel {
      titledRow(message("group.tab.appearance")) {
        row(message("combobox.editor.tab.placement")) {
          myEditorTabPlacement()
        }
        row {
          myScrollTabLayoutInEditorCheckBox()
          row {
            myHideTabsCheckbox()
          }
        }
        row { myShowKnownExtensions() }
        row { myShowDirectoryInTabCheckBox() }
        row { myCbModifiedTabsMarkedWithAsterisk() }
        row { myShowTabsTooltipsCheckBox() }
        myCloseButtonPlacementRow = row(message("tabs.close.button.placement")) {
          myCloseButtonPlacement()
        }
      }
      titledRow(message("group.tab.closing.policy")) {
        row(message("editbox.tab.limit")) {
          myEditorTabLimitField()
        }
        row {
          label(message("label.when.number.of.opened.editors.exceeds.tab.limit"))
          buttonGroup {
            row { myCloseNonModifiedFilesFirstRadio() }
            row { myCloseLRUFilesRadio() }.largeGapAfter()
          }
        }
        row {
          label(message("label.when.closing.active.editor"))
          buttonGroup {
            row { myActivateLeftEditorOnCloseRadio() }
            row { myActivateRightNeighbouringTabRadioButton() }
            row { myActivateMRUEditorOnCloseRadio() }.largeGapAfter()
          }
        }
        row {
          myReuseNotModifiedTabsCheckBox(comment = message("checkbox.smart.tab.reuse.inline.help"))
        }
      }
    }
  }

  override fun reset() {
    val uiSettings = UISettings.instance.state

    myCbModifiedTabsMarkedWithAsterisk.isSelected = uiSettings.markModifiedTabsWithAsterisk
    myShowTabsTooltipsCheckBox.isSelected = uiSettings.showTabsTooltips
    myScrollTabLayoutInEditorCheckBox.isSelected = uiSettings.scrollTabLayoutInEditor
    myHideTabsCheckbox.isEnabled = myScrollTabLayoutInEditorCheckBox.isSelected
    myHideTabsCheckbox.isSelected = uiSettings.hideTabsIfNeed
    myEditorTabPlacement.selectedItem = uiSettings.editorTabPlacement
    myShowKnownExtensions.isSelected = !uiSettings.hideKnownExtensionInTabs
    myShowDirectoryInTabCheckBox.isSelected = uiSettings.showDirectoryForNonUniqueFilenames
    myEditorTabLimitField.text = Integer.toString(uiSettings.editorTabLimit)
    myReuseNotModifiedTabsCheckBox.isSelected = uiSettings.reuseNotModifiedTabs
    myCloseButtonPlacement.selectedItem = getCloseButtonPlacement(uiSettings)

    if (uiSettings.closeNonModifiedFilesFirst) {
      myCloseNonModifiedFilesFirstRadio.isSelected = true
    }
    else {
      myCloseLRUFilesRadio.isSelected = true
    }
    if (uiSettings.activeMruEditorOnClose) {
      myActivateMRUEditorOnCloseRadio.isSelected = true
    }
    else if (uiSettings.activeRightEditorOnClose) {
      myActivateRightNeighbouringTabRadioButton.isSelected = true
    }
    else {
      myActivateLeftEditorOnCloseRadio.isSelected = true
    }
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
    val settingsManager = UISettings.instance
    val uiSettings = settingsManager.state

    var uiSettingsChanged = uiSettings.markModifiedTabsWithAsterisk != myCbModifiedTabsMarkedWithAsterisk.isSelected
    uiSettings.markModifiedTabsWithAsterisk = myCbModifiedTabsMarkedWithAsterisk.isSelected

    if (isModified(myShowTabsTooltipsCheckBox, uiSettings.showTabsTooltips)) uiSettingsChanged = true
    uiSettings.showTabsTooltips = myShowTabsTooltipsCheckBox.isSelected

    if (isModified(myScrollTabLayoutInEditorCheckBox, uiSettings.scrollTabLayoutInEditor)) uiSettingsChanged = true
    uiSettings.scrollTabLayoutInEditor = myScrollTabLayoutInEditorCheckBox.isSelected

    if (isModified(myHideTabsCheckbox, uiSettings.hideTabsIfNeed)) uiSettingsChanged = true
    uiSettings.hideTabsIfNeed = myHideTabsCheckbox.isSelected

    if (isModified(myCloseButtonPlacement, getCloseButtonPlacement(uiSettings))) uiSettingsChanged = true
    val placement = myCloseButtonPlacement.selectedItem as String
    uiSettings.showCloseButton = placement !== NONE
    if (placement !== NONE) {
      uiSettings.closeTabButtonOnTheRight = placement === RIGHT
    }

    val tabPlacement = (myEditorTabPlacement.selectedItem as Int).toInt()
    if (uiSettings.editorTabPlacement != tabPlacement) uiSettingsChanged = true
    uiSettings.editorTabPlacement = tabPlacement

    val hide = !myShowKnownExtensions.isSelected
    if (uiSettings.hideKnownExtensionInTabs != hide) uiSettingsChanged = true
    uiSettings.hideKnownExtensionInTabs = hide

    val dir = myShowDirectoryInTabCheckBox.isSelected
    if (uiSettings.showDirectoryForNonUniqueFilenames != dir) uiSettingsChanged = true
    uiSettings.showDirectoryForNonUniqueFilenames = dir

    uiSettings.closeNonModifiedFilesFirst = myCloseNonModifiedFilesFirstRadio.isSelected
    uiSettings.activeMruEditorOnClose = myActivateMRUEditorOnCloseRadio.isSelected
    uiSettings.activeRightEditorOnClose = myActivateRightNeighbouringTabRadioButton.isSelected

    if (isModified(myReuseNotModifiedTabsCheckBox, uiSettings.reuseNotModifiedTabs)) uiSettingsChanged = true
    uiSettings.reuseNotModifiedTabs = myReuseNotModifiedTabsCheckBox.isSelected

    if (isModified(myEditorTabLimitField, uiSettings.editorTabLimit, EDITOR_TABS_RANGE)) uiSettingsChanged = true
    try {
      uiSettings.editorTabLimit = EDITOR_TABS_RANGE.fit(Integer.parseInt(myEditorTabLimitField.text.trim { it <= ' ' }))
    }
    catch (ignored: NumberFormatException) {
    }

    if (uiSettingsChanged) {
      settingsManager.fireUISettingsChanged()
    }
  }

  override fun isModified(): Boolean {
    val uiSettings = UISettings.instance.state
    var isModified = isModified(myCbModifiedTabsMarkedWithAsterisk, uiSettings.markModifiedTabsWithAsterisk)
    isModified = isModified or isModified(myShowTabsTooltipsCheckBox, uiSettings.showTabsTooltips)
    isModified = isModified or isModified(myEditorTabLimitField, uiSettings.editorTabLimit)
    isModified = isModified or isModified(myReuseNotModifiedTabsCheckBox, uiSettings.reuseNotModifiedTabs)
    val tabPlacement = (myEditorTabPlacement.selectedItem as Int).toInt()
    isModified = isModified or (tabPlacement != uiSettings.editorTabPlacement)
    isModified = isModified or (myShowKnownExtensions.isSelected == uiSettings.hideKnownExtensionInTabs)
    isModified = isModified or (myShowDirectoryInTabCheckBox.isSelected != uiSettings.showDirectoryForNonUniqueFilenames)

    isModified = isModified or (myScrollTabLayoutInEditorCheckBox.isSelected != uiSettings.scrollTabLayoutInEditor)
    isModified = isModified or (myHideTabsCheckbox.isSelected != uiSettings.hideTabsIfNeed)
    isModified = isModified or isModified(myCloseButtonPlacement, getCloseButtonPlacement(uiSettings))

    isModified = isModified or isModified(myCloseNonModifiedFilesFirstRadio, uiSettings.closeNonModifiedFilesFirst)
    isModified = isModified or isModified(myActivateMRUEditorOnCloseRadio, uiSettings.activeMruEditorOnClose)
    isModified = isModified or isModified(myActivateRightNeighbouringTabRadioButton, uiSettings.activeRightEditorOnClose)

    return isModified
  }

  private fun isModified(textField: JTextField, value: Int): Boolean {
    try {
      val fieldValue = Integer.parseInt(textField.text.trim { it <= ' ' })
      return fieldValue != value
    }
    catch (e: NumberFormatException) {
      return false
    }
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