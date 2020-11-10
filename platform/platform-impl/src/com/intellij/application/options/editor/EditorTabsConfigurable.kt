// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.TABS_NONE
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationBundle.message
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.layout.*
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.tabsLayout.TabsLayoutInfo
import com.intellij.ui.tabs.layout.TabsLayoutSettingsUi
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.math.max

class EditorTabsConfigurable : BoundSearchableConfigurable(
  message("configurable.editor.tabs.display.name"),
  "reference.settingsdialog.IDE.editor.tabs",
  ID
), EditorOptionsProvider {
  private lateinit var myEditorTabPlacement: JComboBox<Int>
  private lateinit var myOneRowRadio: JRadioButton
  private lateinit var myMultipleRowsRadio: JRadioButton

  override fun createPanel(): DialogPanel {
    return panel {
      titledRow(message("group.tab.appearance")) {

        if (JBTabsImpl.NEW_TABS) {
          val tabPlacementComboBoxModel: DefaultComboBoxModel<Int> = DefaultComboBoxModel(TAB_PLACEMENTS)
          val myTabsLayoutComboBox: JComboBox<TabsLayoutInfo> = TabsLayoutSettingsUi.tabsLayoutComboBox(tabPlacementComboBoxModel)

          row {
            cell {
              label(message("combobox.editor.tab.tabslayout") + ":")
              val builder = myTabsLayoutComboBox()
              TabsLayoutSettingsUi.prepare(builder, myTabsLayoutComboBox)
            }
          }
          row {
            cell {
              label(TAB_PLACEMENT + ":")
              myEditorTabPlacement = tabPlacementComboBox(tabPlacementComboBoxModel).component
            }
          }

          updateTabPlacementComboBoxVisibility(tabPlacementComboBoxModel)
          tabPlacementComboBoxModel.addListDataListener(MyAnyChangeOfListListener {
            updateTabPlacementComboBoxVisibility(tabPlacementComboBoxModel)
          })
        }
        else {

          row {
            cell {
              label(TAB_PLACEMENT + ":")
              myEditorTabPlacement = tabPlacementComboBox().component
            }
          }
          row {
            label(ApplicationBundle.message("editor.show.tabs.in"))
            row {
              cell(false, false, {
                myOneRowRadio = radioButton(showTabsInOneRow)
                  .enableIf(myEditorTabPlacement.selectedValueIs(SwingConstants.TOP)).component
                checkBox(hideTabsIfNeeded).enableIf(
                  myEditorTabPlacement.selectedValueMatches { it == SwingConstants.TOP || it == SwingConstants.BOTTOM }
                    and myOneRowRadio.selected).withLargeLeftGap().component

              })
            }
            row {
              cell(false, false, {
                myMultipleRowsRadio = radioButton(showTabsInMultipleRows)
                  .enableIf(myEditorTabPlacement.selectedValueIs(SwingConstants.TOP)).component
                checkBox(showPinnedTabsInASeparateRow).enableIf(
                  myEditorTabPlacement.selectedValueIs(SwingConstants.TOP)
                    and myMultipleRowsRadio.selected).withLargeLeftGap().component
              })
            }
            val group = ButtonGroup()
            group.add(myOneRowRadio)
            group.add(myMultipleRowsRadio)
          }
        }
        row { checkBox(useSmallFont).enableIfTabsVisible() }
        row { checkBox(showFileIcon).enableIfTabsVisible() }
        row { checkBox(showFileExtension).enableIfTabsVisible() }
        row { checkBox(showDirectoryForNonUniqueFilenames).enableIfTabsVisible() }
        row { checkBox(markModifiedTabsWithAsterisk).enableIfTabsVisible() }
        row { checkBox(showTabsTooltips).enableIfTabsVisible() }
        row {
          cell {
            label(CLOSE_BUTTON_POSITION + ":")
            closeButtonPositionComboBox()
          }
        }.enableIf((myEditorTabPlacement.selectedValueMatches { it != TABS_NONE }))
      }
      titledRow(message("group.tab.order")) {
        row { checkBox(sortTabsAlphabetically) }
        row { checkBox(openTabsAtTheEnd) }
      }
      titledRow(message("group.tab.opening.policy")) {
        row {
          checkBox(openInPreviewTabIfPossible)
        }
        row {
          checkBox(reuseNotModifiedTabs)
        }
        row {
          checkBox(openTabsInMainWindow)
        }
      }
      titledRow(message("group.tab.closing.policy")) {
        row {
          cell {
            label(message("editbox.tab.limit"))
            intTextField(ui::editorTabLimit, 4, 1..max(10, Registry.intValue("ide.max.editor.tabs", 100)))
          }
        }
        row {
          buttonGroup(ui::closeNonModifiedFilesFirst) {
            checkBoxGroup(message("label.when.number.of.opened.editors.exceeds.tab.limit")) {
              row { radioButton(message("radio.close.non.modified.files.first"), value = true) }
              row { radioButton(message("radio.close.less.frequently.used.files"), value = false) }.largeGapAfter()
            }
          }
        }
        row {
          buttonGroup(message("label.when.closing.active.editor")) {
            row {
              radioButton(message("radio.activate.left.neighbouring.tab")).apply {
                onReset { component.isSelected = !ui.activeRightEditorOnClose && !ui.activeMruEditorOnClose }
              }
            }
            row { radioButton(message("radio.activate.right.neighbouring.tab"), ui::activeRightEditorOnClose) }
            row { radioButton(message("radio.activate.most.recently.opened.tab"), ui::activeMruEditorOnClose) }.largeGapAfter()
          }
        }
      }
    }
  }

  private fun updateTabPlacementComboBoxVisibility(tabPlacementComboBoxModel: DefaultComboBoxModel<Int>) {
    myEditorTabPlacement.isEnabled = tabPlacementComboBoxModel.size > 1
  }

  private fun <T : JComponent> CellBuilder<T>.enableIfTabsVisible() {
    enableIf(myEditorTabPlacement.selectedValueMatches { it != TABS_NONE })
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
