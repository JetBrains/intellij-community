/*******************************************************************************
 * Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 ******************************************************************************/
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.TABS_NONE
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.NotABooleanOptionDescription
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.toNullableProperty
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import org.jetbrains.annotations.Nls
import javax.swing.SwingConstants.*

private val TAB_PLACEMENTS = listOf(TOP, LEFT, BOTTOM, RIGHT, TABS_NONE)

internal val TAB_PLACEMENT: @Nls String = ApplicationBundle.message("combobox.editor.tab.placement")

internal val tabPlacementsOptionDescriptors: List<BooleanOptionDescription> = TAB_PLACEMENTS.map { i -> asOptionDescriptor(i) }

internal fun Row.tabPlacementComboBox(): Cell<ComboBox<Int>> {
  val ui = UISettings.getInstance().state
  return comboBox(TAB_PLACEMENTS, textListCellRenderer { it?.asTabPlacement() })
    .bindItem(ui::editorTabPlacement.toNullableProperty())
}

private fun asOptionDescriptor(i: Int): BooleanOptionDescription {
  return object : BooleanOptionDescription(TAB_PLACEMENT + " | " + i.asTabPlacement(), EDITOR_TABS_OPTIONS_ID), NotABooleanOptionDescription {
    override fun isOptionEnabled() = UISettings.getInstance().state.editorTabPlacement == i

    override fun setOptionState(enabled: Boolean) {
      val ui = UISettings.getInstance()
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
