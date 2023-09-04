// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.tree

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.GotItTooltip
import com.intellij.ui.SpeedSearchBase
import com.intellij.ui.speedSearch.SpeedSearchSupply
import java.awt.Component
import java.awt.Point
import javax.swing.JComponent

internal class SpeedSearchActionHandler(private val speedSearch: SpeedSearchBase<*>) {

  fun isSpeedSearchActive(): Boolean = speedSearch.isPopupActive

  fun activateSpeedSearch() {
    if (speedSearch.isAvailable && !isSpeedSearchActive()) {
      speedSearch.showPopup()
      val component = speedSearch.searchField ?: return
      val shortcut = getActionShortcut()
      val gotItMessage = if (shortcut == null) {
        ActionsBundle.message("action.Tree-speedSearch.GotItTooltip.textWithoutShortcuts")
      }
      else {
        ActionsBundle.message("action.Tree-speedSearch.GotItTooltip.text", shortcut)
      }
      GotItTooltip("speed.search.shown", gotItMessage)
        .show(component) { c, _ -> Point(c.width, c.height / 2) }
    }
  }

  private fun getActionShortcut(): String? =
    ActionManager.getInstance().getAction(TreeSpeedSearchAction.ID)
      ?.shortcutSet
      ?.shortcuts
      ?.firstOrNull()
      ?.let { KeymapUtil.getShortcutText(it) }

}

internal fun Component.getSpeedSearchActionHandler(): SpeedSearchActionHandler? {
  val contextComponent = (this as? JComponent?) ?: return null
  val speedSearch = (SpeedSearchSupply.getSupply(contextComponent, true) as? SpeedSearchBase<*>?) ?: return null
  if (!speedSearch.isAvailable) return null
  return SpeedSearchActionHandler(speedSearch)
}
