// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selectedValueIs
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.HierarchyEvent

@ApiStatus.Internal
class TemplateExpandShortcutPanel(label: @NlsContexts.Label String) {

  val panel: DialogPanel = panel {
    row(label) {
      myExpandByCombo = comboBox(listOf(
        space,
        tab,
        enter,
        custom
      ), textListCellRenderer<String?>("") {
        if (it == custom) {
          val shortcuts = getCurrentCustomShortcuts()
          val shortcutText = if (shortcuts.isEmpty()) "" else KeymapUtil.getShortcutsText(shortcuts)
          if (StringUtil.isEmpty(shortcutText))
            ApplicationBundle.message("custom.option")
          else
            ApplicationBundle.message("custom.option.with.shortcut", shortcutText)
        }
        else {
          it
        }
      }).component

      myOpenKeymapLabel = link(CodeInsightBundle.message("link.change.context")) {
        val allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(myOpenKeymapLabel))
        val keymapPanel = if (allSettings == null) KeymapPanel() else allSettings.find(KeymapPanel::class.java) ?: return@link
        val selectAction = Runnable { keymapPanel.selectAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_CUSTOM) }
        if (allSettings != null) {
          allSettings.select(keymapPanel).doWhenDone(selectAction)
        }
        else {
          ShowSettingsUtil.getInstance().editConfigurable(myOpenKeymapLabel, keymapPanel, selectAction)
          resizeComboToFitCustomShortcut()
        }
      }.visibleIf(myExpandByCombo.selectedValueIs(custom))
        .component
    }
  }.apply {
    addHierarchyListener {
      if (it.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && isShowing) {
        resizeComboToFitCustomShortcut()
      }
    }
  }

  private lateinit var myExpandByCombo: ComboBox<String>
  private lateinit var myOpenKeymapLabel: ActionLink

  private fun getCurrentCustomShortcuts(): Array<Shortcut?> {
    val allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(myOpenKeymapLabel))
    val keymapPanel = allSettings?.find(KeymapPanel::class.java)
    var shortcuts = keymapPanel?.getCurrentShortcuts(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_CUSTOM)
    if (shortcuts == null) {
      val shortcut = ActionManager.getInstance().getKeyboardShortcut(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_CUSTOM)
      shortcuts = if (shortcut == null) Shortcut.EMPTY_ARRAY else arrayOf<Shortcut>(shortcut)
    }
    return shortcuts
  }

  val selectedString: String?
    get() = myExpandByCombo.selectedItem as String?

  var selectedChar: Char
    get() {
      return when (myExpandByCombo.selectedItem) {
        tab -> TemplateSettings.TAB_CHAR
        enter -> TemplateSettings.ENTER_CHAR
        space -> TemplateSettings.SPACE_CHAR
        else -> TemplateSettings.CUSTOM_CHAR
      }
    }
    set(ch) {
      val value = when (ch) {
        TemplateSettings.SPACE_CHAR -> space
        TemplateSettings.TAB_CHAR -> tab
        TemplateSettings.ENTER_CHAR -> enter
        else -> custom
      }

      myExpandByCombo.setSelectedItem(value)
    }

  private fun resizeComboToFitCustomShortcut() {
    myExpandByCombo.setPrototypeDisplayValue(null)
    myExpandByCombo.setPrototypeDisplayValue(custom)
    myExpandByCombo.revalidate()
    myExpandByCombo.repaint()
  }
}

private val space: @Nls String
  get() = CodeInsightBundle.message("template.shortcut.space")

private val tab: @Nls String
  get() = CodeInsightBundle.message("template.shortcut.tab")

private val enter: @Nls String
  get() = CodeInsightBundle.message("template.shortcut.enter")

private val custom: @Nls String
  get() = CodeInsightBundle.message("template.shortcut.custom")
