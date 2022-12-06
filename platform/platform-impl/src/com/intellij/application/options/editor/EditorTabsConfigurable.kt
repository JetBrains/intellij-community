// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.TABS_NONE
import com.intellij.openapi.application.ApplicationBundle.message
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.SwingConstants
import kotlin.math.max

internal class EditorTabsConfigurable : BoundCompositeSearchableConfigurable<SearchableConfigurable>(
  message("configurable.editor.tabs.display.name"),
  "reference.settingsdialog.IDE.editor.tabs",
  EDITOR_TABS_OPTIONS_ID
), EditorOptionsProvider, WithEpDependencies {
  private lateinit var myEditorTabPlacement: JComboBox<Int>
  private lateinit var myOneRowCheckbox: JCheckBox

  override fun createConfigurables(): List<SearchableConfigurable> =
    ConfigurableWrapper.createConfigurables(EditorTabsConfigurableEP.EP_NAME)

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> =
    listOf(EditorTabsConfigurableEP.EP_NAME)

  override fun createPanel(): DialogPanel {
    val ui = UISettings.getInstance().state
    return panel {
      group(message("group.tab.appearance")) {
        row(TAB_PLACEMENT + ":") {
          myEditorTabPlacement = tabPlacementComboBox().component
        }

        if (ExperimentalUI.isNewUI()) {
          @Suppress("DialogTitleCapitalization")
          buttonsGroup(message("button.group.title.show.tabs.in")) {
            lateinit var singleRowButton: JBRadioButton
            row {
              singleRowButton = radioButton(message("radio.one.row"), value = true)
                .enabledIf(myEditorTabPlacement.selectedValueMatches { it == SwingConstants.TOP || it == SwingConstants.BOTTOM })
                .component
            }

            buttonsGroup(indent = true) {
              row { radioButton(message("radio.scroll.tabs.panel"), value = true) }
              row { radioButton(message("radio.squeeze.tabs"), value = false) }
            }.bind(ui::hideTabsIfNeeded)
              .enabledIf(myEditorTabPlacement.selectedValueMatches { it == SwingConstants.TOP || it == SwingConstants.BOTTOM } and singleRowButton.selected)

            row {
              radioButton(message("radio.multiple.rows"), value = false)
                .enabledIf(myEditorTabPlacement.selectedValueIs(SwingConstants.TOP))
            }

            myEditorTabPlacement.addActionListener {
              // move selection to single row, because it is the only one option in the bottom placement
              if (myEditorTabPlacement.selectedItem == SwingConstants.BOTTOM) {
                singleRowButton.isSelected = true
              }
            }
          }.bind(ui::scrollTabLayoutInEditor)
        }
        else {
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

        row { checkBox(showPinnedTabsInASeparateRow).enabledIf(myEditorTabPlacement.selectedValueIs(SwingConstants.TOP)
                                                                 and AdvancedSettingsPredicate("editor.keep.pinned.tabs.on.left", disposable!!)) }
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
        row { checkBox(sortTabsAlphabetically).onApply { resetAlwaysKeepSorted() } }
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

      addSections()
    }
  }

  private fun <T : JComponent> Cell<T>.enableIfTabsVisible() {
    enabledIf(myEditorTabPlacement.selectedValueMatches { it != TABS_NONE })
  }
  override fun apply() {
    val uiSettingsChanged = isModified
    super.apply()

    if (uiSettingsChanged) {
      UISettings.getInstance().fireUISettingsChanged()
    }
  }

  private fun Panel.addSections() {
    configurables.filterIsInstance<EditorTabsOptionsCustomSection>()
      .sortedWith(Comparator.comparing { c ->
        (c as? Configurable)?.displayName ?: ""
      })
      .forEach { appendDslConfigurable(it) }
  }
}

  private fun resetAlwaysKeepSorted() {
    if (!UISettings.getInstance().sortTabsAlphabetically) {
      UISettings.getInstance().alwaysKeepTabsAlphabeticallySorted = false
    }
  }

