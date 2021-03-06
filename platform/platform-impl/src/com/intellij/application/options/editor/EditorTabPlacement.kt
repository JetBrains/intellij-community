// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.TABS_NONE
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.NotABooleanOptionDescription
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.SwingConstants.*

internal val TAB_PLACEMENTS = arrayOf(TOP, LEFT, BOTTOM, RIGHT, TABS_NONE)

internal val TAB_PLACEMENT = ApplicationBundle.message("combobox.editor.tab.placement")

internal val tabPlacementsOptionDescriptors = TAB_PLACEMENTS.map<Int, BooleanOptionDescription> { i -> asOptionDescriptor(i) }

internal fun Cell.tabPlacementComboBox(): CellBuilder<ComboBox<Int>> {
  return tabPlacementComboBox(DefaultComboBoxModel<Int>(TAB_PLACEMENTS))
}

internal fun Cell.tabPlacementComboBox(model: ComboBoxModel<Int>): CellBuilder<ComboBox<Int>> {
  return comboBox(model,
                  ui::editorTabPlacement,
                  renderer = SimpleListCellRenderer.create<Int> { label, value, _ ->
                    label.text = value.asTabPlacement()
                  })
}

private fun asOptionDescriptor(i: Int): BooleanOptionDescription {
  return object : BooleanOptionDescription(TAB_PLACEMENT + " | " + i.asTabPlacement(), ID), NotABooleanOptionDescription {
    override fun isOptionEnabled() = ui.editorTabPlacement == i

    override fun setOptionState(enabled: Boolean) {
      ui.editorTabPlacement = next(ui.editorTabPlacement, enabled)
      UISettings.instance.fireUISettingsChanged()
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
