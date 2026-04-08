// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBLabel
import java.awt.Rectangle
import javax.swing.Icon

data class MinimapBalloonState(
  var balloon: Balloon? = null,
  var tracker: MinimapHoverBalloonTracker? = null,
  var label: JBLabel? = null,
  var lastText: String? = null,
  var lastRect: Rectangle? = null,
  var lastIcon: Icon? = null
) {
  fun isSame(text: String, rect: Rectangle, icon: Icon?): Boolean {
    return lastText == text && lastRect == rect && lastIcon === icon
  }

  fun hasActiveBalloon(): Boolean {
    return balloon != null && label != null
  }

  fun updateLabelIfNeeded(@NlsSafe text: String, icon: Icon?) {
    val existingLabel = label ?: return
    if (lastText != text) {
      existingLabel.text = text
      lastText = text
    }
    if (lastIcon !== icon) {
      existingLabel.icon = icon
      lastIcon = icon
    }
  }

  fun updateRect(rect: Rectangle) {
    lastRect = rect
  }

  fun install(balloon: Balloon, label: JBLabel, tracker: MinimapHoverBalloonTracker, text: String, rect: Rectangle, icon: Icon?) {
    this.balloon = balloon
    this.label = label
    this.tracker = tracker
    lastText = text
    lastRect = rect
    lastIcon = icon
  }

  fun hideAndClear() {
    balloon?.hide(true)
    balloon = null
    tracker = null
    label = null
    lastText = null
    lastRect = null
    lastIcon = null
  }
}
