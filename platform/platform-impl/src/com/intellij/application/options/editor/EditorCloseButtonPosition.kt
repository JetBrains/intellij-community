// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.openapi.application.ApplicationBundle.message
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.layout.*
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

private const val LEFT = "Left"
private const val RIGHT = "Right"
private const val NONE = "None"

private val items = listOf(LEFT, RIGHT, NONE)

@Nls
private fun optionName(@NonNls option: String): String {
  return when (option) {
    LEFT -> message("combobox.tab.placement.left")
    RIGHT -> message("combobox.tab.placement.right")
    else -> message("combobox.tab.placement.none")
  }
}

internal val CLOSE_BUTTON_POSITION: String
  get() = message("tabs.close.button.placement")

internal fun Row.closeButtonPositionComboBox() {
  comboBox(CollectionComboBoxModel(items), listCellRenderer { value, _, _ -> text = optionName(value) })
    .bindItem({ getCloseButtonPlacement() }, { set(it) })
}

internal fun closeButtonPlacementOptionDescription(): Collection<BooleanOptionDescription> = items.map { asOptionDescriptor(it) }

private fun set(s: String?) {
  val ui = UISettings.instance.state
  ui.showCloseButton = s != NONE
  if (s != NONE) {
    ui.closeTabButtonOnTheRight = s == RIGHT
  }
}

private fun asOptionDescriptor(s: String): BooleanOptionDescription {
  return object : BooleanOptionDescription(CLOSE_BUTTON_POSITION + " | " + optionName(s), EDITOR_TABS_OPTIONS_ID) {
    override fun isOptionEnabled() = getCloseButtonPlacement() === s

    override fun setOptionState(enabled: Boolean) {
      when {
        enabled -> set(s)
        else -> set(when {
                      s === RIGHT -> LEFT
                      else -> RIGHT
                    })
      }
      UISettings.instance.fireUISettingsChanged()
    }
  }
}

private fun getCloseButtonPlacement(): String {
  val ui = UISettings.instance.state
  return when {
    !ui.showCloseButton -> NONE
    java.lang.Boolean.getBoolean("closeTabButtonOnTheLeft") || !ui.closeTabButtonOnTheRight -> LEFT
    else -> RIGHT
  }
}