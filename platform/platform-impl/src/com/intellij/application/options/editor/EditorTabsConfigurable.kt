// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.TABS_NONE
import com.intellij.openapi.application.ApplicationBundle.message
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo
import com.intellij.ui.tabs.layout.TabsLayoutSettingsUi
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.math.max

internal class EditorTabsConfigurable : BoundSearchableConfigurable(
  message("configurable.editor.tabs.display.name"),
  "reference.settingsdialog.IDE.editor.tabs",
  EDITOR_TABS_OPTIONS_ID
), EditorOptionsProvider {
  private lateinit var myEditorTabPlacement: JComboBox<Int>
  private lateinit var myOneRowCheckbox: JCheckBox

  override fun createPanel(): DialogPanel {
    val ui = UISettings.instance.state
    return panel {
      group(message("group.tab.appearance")) {

        if (JBTabsImpl.NEW_TABS) {
          val tabPlacementComboBoxModel: DefaultComboBoxModel<Int> = DefaultComboBoxModel(TAB_PLACEMENTS)
          val myTabsLayoutComboBox: JComboBox<TabsLayoutInfo> = TabsLayoutSettingsUi.tabsLayoutComboBox(tabPlacementComboBoxModel)

          row(message("combobox.editor.tab.tabslayout") + ":") {
            val builder = cell(myTabsLayoutComboBox)
            TabsLayoutSettingsUi.prepare(builder, myTabsLayoutComboBox)
          }.layout(RowLayout.INDEPENDENT)
          row(TAB_PLACEMENT + ":") {
            myEditorTabPlacement = tabPlacementComboBox(tabPlacementComboBoxModel).component
          }.layout(RowLayout.INDEPENDENT)

          updateTabPlacementComboBoxVisibility(tabPlacementComboBoxModel)
          tabPlacementComboBoxModel.addListDataListener(MyAnyChangeOfListListener {
            updateTabPlacementComboBoxVisibility(tabPlacementComboBoxModel)
          })
        }
        else {
          row(TAB_PLACEMENT + ":") {
            myEditorTabPlacement = tabPlacementComboBox().component
          }
          if (ExperimentalUI.isNewEditorTabs()) {
            row {
              checkBox(hideTabsIfNeeded)
                .enabledIf(myEditorTabPlacement.selectedValueMatches { it == SwingConstants.TOP })
                .component
            }
          } else {
            row {
              myOneRowCheckbox = checkBox(showTabsInOneRow)
                .enabledIf(myEditorTabPlacement.selectedValueIs(SwingConstants.TOP)).component
            }
            indent {
              row {
                checkBox(hideTabsIfNeeded).enabledIf(
                  myEditorTabPlacement.selectedValueMatches { it == SwingConstants.TOP || it == SwingConstants.BOTTOM }
                    and myOneRowCheckbox.selected).component
              }
            }
          }
          row { checkBox(showPinnedTabsInASeparateRow).enabledIf(myEditorTabPlacement.selectedValueIs(SwingConstants.TOP)) }
        }
        row { checkBox(useSmallFont).enableIfTabsVisible() }.visible(!ExperimentalUI.isNewUI())
        row { checkBox(showFileIcon).enableIfTabsVisible() }
        row { checkBox(showFileExtension).enableIfTabsVisible() }
        row { checkBox(showDirectoryForNonUniqueFilenames).enableIfTabsVisible() }
        row { checkBox(markModifiedTabsWithAsterisk).enableIfTabsVisible() }
        row { checkBox(showTabsTooltips).enableIfTabsVisible() }
        row(CLOSE_BUTTON_POSITION + ":") {
          closeButtonPositionComboBox()
        }.enabledIf((myEditorTabPlacement.selectedValueMatches { it != TABS_NONE }))
      }
      group(message("group.tab.order")) {
        row { checkBox(sortTabsAlphabetically) }
        row { checkBox(openTabsAtTheEnd) }
      }
      group(message("group.tab.opening.policy")) {
        row {
          checkBox(openInPreviewTabIfPossible)
        }
      }
      group(message("group.tab.closing.policy")) {
        row(message("editbox.tab.limit")) {
          intTextField(1..max(10, Registry.intValue("ide.max.editor.tabs", 100)))
            .columns(4)
            .bindIntText(ui::editorTabLimit)
        }
        buttonsGroup(message("label.when.number.of.opened.editors.exceeds.tab.limit")) {
          row { radioButton(message("radio.close.non.modified.files.first"), value = true) }
          row { radioButton(message("radio.close.less.frequently.used.files"), value = false) }
            .bottomGap(BottomGap.SMALL)
        }.bind(ui::closeNonModifiedFilesFirst)
        buttonsGroup(message("label.when.closing.active.editor")) {
          row {
            radioButton(message("radio.activate.left.neighbouring.tab")).apply {
              onReset { component.isSelected = !ui.activeRightEditorOnClose && !ui.activeMruEditorOnClose }
            }
          }
          row { radioButton(message("radio.activate.right.neighbouring.tab")).bindSelected(ui::activeRightEditorOnClose) }
          row { radioButton(message("radio.activate.most.recently.opened.tab")).bindSelected(ui::activeMruEditorOnClose) }
        }
      }
    }
  }

  private fun updateTabPlacementComboBoxVisibility(tabPlacementComboBoxModel: DefaultComboBoxModel<Int>) {
    myEditorTabPlacement.isEnabled = tabPlacementComboBoxModel.size > 1
  }

  private fun <T : JComponent> Cell<T>.enableIfTabsVisible() {
    enabledIf(myEditorTabPlacement.selectedValueMatches { it != TABS_NONE })
  }

  override fun apply() {
    val uiSettingsChanged = isModified
    super.apply()

    if (uiSettingsChanged) {
      UISettings.instance.fireUISettingsChanged()
    }
  }

  private class MyAnyChangeOfListListener(val action: () -> Unit) : ListDataListener {
    override fun contentsChanged(e: ListDataEvent?) { action() }
    override fun intervalRemoved(e: ListDataEvent?) { action() }
    override fun intervalAdded(e: ListDataEvent?) { action() }
  }
}
