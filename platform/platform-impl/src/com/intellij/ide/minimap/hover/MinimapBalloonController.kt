// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.SwingConstants

class MinimapBalloonController(private val panel: MinimapPanel) {
  private val balloonState = MinimapBalloonState()
  private val settingsState = MinimapSettings.getInstance().state

  fun show(@NlsSafe text: String, rect: Rectangle, icon: Icon?) {
    if (!panel.isShowing) {
      hide()
      return
    }
    if (balloonState.isSame(text, rect, icon)) return

    if (balloonState.hasActiveBalloon()) {
      balloonState.updateLabelIfNeeded(text, icon)
      balloonState.updateRect(rect)
      balloonState.tracker?.refresh()
      return
    }

    hide()

    val createdLabel = JBLabel(text, icon, SwingConstants.LEADING)
    val created = createBalloon(createdLabel)
    val position = if (settingsState.rightAligned) Balloon.Position.atLeft else Balloon.Position.atRight
    val newTracker = MinimapHoverBalloonTracker(panel, settingsState) { balloonState.lastRect }

    balloonState.install(created, createdLabel, newTracker, text, rect, icon)
    created.show(newTracker, position)
  }

  fun hide() {
    balloonState.hideAndClear()
  }

  private fun createBalloon(label: JBLabel): Balloon {
    return JBPopupFactory.getInstance()
      .createBalloonBuilder(label)
      .setShowCallout(false)
      .setAnimationCycle(0)
      .setHideOnClickOutside(false)
      .setHideOnKeyOutside(false)
      .setHideOnAction(false)
      .setHideOnFrameResize(true)
      .setHideOnLinkClick(false)
      .setFadeoutTime(0)
      .createBalloon()
  }
}
