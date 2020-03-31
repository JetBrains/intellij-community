// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.openapi.application.ApplicationBundle.message
import com.intellij.ui.layout.*
import javax.swing.DefaultComboBoxModel

private const val LEFT = "Left"
private const val RIGHT = "Right"
private const val NONE = "None"

private val items = arrayOf(LEFT, RIGHT, NONE)

internal val CLOSE_BUTTON_POSITION = message("tabs.close.button.placement")

internal fun Cell.closeButtonPositionComboBox() {
  comboBox(DefaultComboBoxModel<String>(items),
           { getCloseButtonPlacement() },
           { set(it) }
  )
}

internal val closeButtonPlacementOptionDescription: Collection<BooleanOptionDescription> = items.map { asOptionDescriptor(it) }

private fun set(s: String?) {
  ui.showCloseButton = s !== NONE
  if (s !== NONE) {
    ui.closeTabButtonOnTheRight = s == RIGHT
  }
}

private fun asOptionDescriptor(s: String) = object : BooleanOptionDescription(CLOSE_BUTTON_POSITION + " | " + s, ID) {
  override fun isOptionEnabled() = getCloseButtonPlacement() === s

  override fun setOptionState(enabled: Boolean) {
    when {
      enabled -> set(s)
      else -> set(
        when {
          s === RIGHT -> LEFT
          else -> RIGHT
        })
    }
    UISettings.instance.fireUISettingsChanged()
  }
}

private fun getCloseButtonPlacement() = when {
  !ui.showCloseButton -> NONE
  java.lang.Boolean.getBoolean("closeTabButtonOnTheLeft") || !ui.closeTabButtonOnTheRight -> LEFT
  else -> RIGHT
}