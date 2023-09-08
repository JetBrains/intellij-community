// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.speedSearch

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.GotItTooltip
import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import java.awt.Component
import java.awt.Point
import javax.swing.JComponent

internal class SpeedSearchActionHandler(val targetComponent: JComponent, private val speedSearch: SpeedSearchBase<*>) {

  var requestFocus = false

  var showGotItTooltip = false

  fun isSpeedSearchAvailable(): Boolean = speedSearch.isAvailable

  fun isSpeedSearchActive(): Boolean = speedSearch.isPopupActive

  fun activateSpeedSearch() {
    if (isSpeedSearchAvailable() && !isSpeedSearchActive()) {
      if (requestFocus) {
        targetComponent.requestFocusInWindow()
      }
      doActivateSpeedSearch()
    }
  }

  private fun doActivateSpeedSearch() {
    speedSearch.showPopup()
    val component = speedSearch.searchField ?: return
    val shortcut = getActionShortcut()
    val gotItMessage = if (shortcut == null) {
      ActionsBundle.message("action.SpeedSearch.GotItTooltip.textWithoutShortcuts")
    }
    else {
      ActionsBundle.message("action.SpeedSearch.GotItTooltip.text", shortcut)
    }
    if (showGotItTooltip) {
      GotItTooltip("speed.search.shown", gotItMessage)
        .withPosition(Balloon.Position.atRight)
        .show(component) { c, _ -> Point(c.width, c.height / 2) }
    }
  }

  private fun getActionShortcut(): String? =
    ActionManager.getInstance().getAction(SpeedSearchAction.ID)
      ?.shortcutSet
      ?.shortcuts
      ?.firstOrNull()
      ?.let { KeymapUtil.getShortcutText(it) }

}

internal fun Component.getSpeedSearchActionHandler(): SpeedSearchActionHandler? {
  val contextComponent = (this as? JComponent?) ?: return null
  val speedSearch = (SpeedSearchSupply.getSupply(contextComponent, true) as? SpeedSearchBase<*>?) ?: return null
  return SpeedSearchActionHandler(contextComponent, speedSearch)
}
