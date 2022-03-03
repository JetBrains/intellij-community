// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.TABS_NONE
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.NotABooleanOptionDescription
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import org.jetbrains.annotations.Nls
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingConstants.*

internal val TAB_PLACEMENTS = arrayOf(TOP, LEFT, BOTTOM, RIGHT, TABS_NONE)
internal val EXP_UI_TAB_PLACEMENTS = arrayOf(TOP, TABS_NONE)

internal val TAB_PLACEMENT = ApplicationBundle.message("combobox.editor.tab.placement")

internal val tabPlacementsOptionDescriptors = TAB_PLACEMENTS.map { i -> asOptionDescriptor(i) }

internal fun Row.tabPlacementComboBox(): Cell<ComboBox<Int>> {
  val model = if (ExperimentalUI.isNewEditorTabs()) DefaultComboBoxModel(EXP_UI_TAB_PLACEMENTS)
              else DefaultComboBoxModel(TAB_PLACEMENTS)
  return tabPlacementComboBox(model)
}

internal fun Row.tabPlacementComboBox(model: ComboBoxModel<Int>): Cell<ComboBox<Int>> {
  val ui = UISettings.instance.state
  return comboBox(model,
                  renderer = SimpleListCellRenderer.create { label, value, _ ->
                    label.text = value.asTabPlacement()
                  }).bindItem(ui::editorTabPlacement)
}

private fun asOptionDescriptor(i: Int): BooleanOptionDescription {
  return object : BooleanOptionDescription(TAB_PLACEMENT + " | " + i.asTabPlacement(), EDITOR_TABS_OPTIONS_ID), NotABooleanOptionDescription {
    override fun isOptionEnabled() = UISettings.instance.state.editorTabPlacement == i

    override fun setOptionState(enabled: Boolean) {
      val ui = UISettings.instance
      ui.state.editorTabPlacement = next(ui.editorTabPlacement, enabled)
      ui.fireUISettingsChanged()
    }

    private fun next(prev: Int, enabled: Boolean) = when {
      prev != i && enabled -> i
      prev == i -> if (i == TABS_NONE) TOP else TABS_NONE
      else -> prev
    }
  }
}

@Nls
private fun Int.asTabPlacement(): String {
  return when (this) {
    TABS_NONE -> ApplicationBundle.message("combobox.tab.placement.none")
    TOP -> ApplicationBundle.message("combobox.tab.placement.top")
    LEFT -> ApplicationBundle.message("combobox.tab.placement.left")
    BOTTOM -> ApplicationBundle.message("combobox.tab.placement.bottom")
    RIGHT -> ApplicationBundle.message("combobox.tab.placement.right")
    else -> throw IllegalArgumentException("unknown tabPlacement: $this")
  }
}
