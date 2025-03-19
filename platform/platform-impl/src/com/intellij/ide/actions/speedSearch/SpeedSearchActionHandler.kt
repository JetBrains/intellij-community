// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.speedSearch

import com.intellij.ide.IdeEventQueue
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.ComponentUtil
import com.intellij.ui.GotItTooltip
import com.intellij.ui.speedSearch.SpeedSearchActivator
import com.intellij.ui.speedSearch.SpeedSearchSupply
import java.awt.Component
import java.awt.Point
import java.awt.event.KeyEvent
import javax.swing.JComponent

internal class SpeedSearchActionHandler(val targetComponent: JComponent, private val speedSearch: SpeedSearchActivator) {

  var requestFocus = false

  var showGotItTooltip = false

  fun isSpeedSearchAvailable(): Boolean = speedSearch.isAvailable

  fun isSpeedSearchActive(): Boolean = speedSearch.isActive

  fun activateSpeedSearch() {
    if (isSpeedSearchAvailable() && !isSpeedSearchActive()) {
      if (requestFocus) {
        targetComponent.requestFocusInWindow()
      }
      doActivateSpeedSearch()
    }
  }

  private fun doActivateSpeedSearch() {
    speedSearch.activate()
    val component = speedSearch.textField ?: return
    if (showGotItTooltip) {
      showGotItTooltip(component)
    }
  }

  private fun showGotItTooltip(component: JComponent) {
    val shortcut = getActionShortcut()
    val gotItMessage = if (shortcut == null) {
      ActionsBundle.message("action.SpeedSearch.GotItTooltip.textWithoutShortcuts")
    }
    else {
      ActionsBundle.message("action.SpeedSearch.GotItTooltip.text", shortcut)
    }
    val gotItTooltip = GotItTooltip("speed.search.shown", gotItMessage).withPosition(Balloon.Position.atRight)
    IdeEventQueue.getInstance().addDispatcher({ event ->
      when {
        event !is KeyEvent -> false
        // A single key press will generate at least three events: key pressed, key typed and key released.
        // We don't want to any of these to reach the component if this key press is intended to close the got it tooltip.
        // So we only close it on the last one, which is always a key released event, and suppress the previous events.
        isPreCloseGotItEvent(event, component) -> true
        isCloseGotItEvent(event, component) -> {
          gotItTooltip.hidePopup(ok = true)
          true
        }
        else -> false
      }
    }, parent = gotItTooltip)
    gotItTooltip.show(component) { c, _ -> Point(c.width, c.height / 2) }
  }

  private fun isPreCloseGotItEvent(event: KeyEvent, component: JComponent): Boolean =
    isInOurFrame(event, component) && isGotItCloseKey(event) && (event.id == KeyEvent.KEY_PRESSED || event.id == KeyEvent.KEY_TYPED)

  private fun isCloseGotItEvent(event: KeyEvent, component: JComponent): Boolean =
    isInOurFrame(event, component) && isGotItCloseKey(event) && event.id == KeyEvent.KEY_RELEASED

  private fun isInOurFrame(event: KeyEvent, component: JComponent): Boolean =
    ComponentUtil.getWindow(component) == ComponentUtil.getWindow(event.source as? Component)

  private fun isGotItCloseKey(event: KeyEvent): Boolean =
    event.modifiersEx == 0 &&
    if (event.id == KeyEvent.KEY_TYPED) event.keyChar in GOT_IT_CLOSE_KEY_CHARS else event.keyCode in GOT_IT_CLOSE_KEY_CODES

  private fun getActionShortcut(): String? =
    ActionManager.getInstance().getAction(SpeedSearchAction.ID)
      ?.shortcutSet
      ?.shortcuts
      ?.firstOrNull()
      ?.let { KeymapUtil.getShortcutText(it) }

}

internal fun Component.getSpeedSearchActionHandler(): SpeedSearchActionHandler? {
  val contextComponent = (this as? JComponent?) ?: return null
  val speedSearch = (SpeedSearchSupply.getSupply(contextComponent, true) as? SpeedSearchActivator?) ?: return null
  if (!speedSearch.isSupported) return null
  return SpeedSearchActionHandler(contextComponent, speedSearch)
}

private val GOT_IT_CLOSE_KEY_CODES = listOf(KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE)
private val GOT_IT_CLOSE_KEY_CHARS = listOf('\u001B', '\n', ' ')
