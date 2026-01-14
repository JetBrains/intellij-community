// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.client.ClientSystemInfo
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

data class ClickModifiers(
  val isCtrlPressed: Boolean = false,
  val isShiftPressed: Boolean = false,
  val isAltPressed: Boolean = false,
  val mouseButton: Int = MouseEvent.BUTTON1
) {
  companion object {
    fun fromEvent(e: MouseEvent) = ClickModifiers(
      isCtrlPressed = e.isCtrlPressed(),
      isShiftPressed = e.isShiftPressed(),
      isAltPressed = e.isAltPressed(),
      mouseButton = e.button
    )
  }
}

private fun MouseEvent.isCtrlPressed(): Boolean =
  (modifiersEx and if (ClientSystemInfo.isMac()) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK) != 0

private fun MouseEvent.isShiftPressed(): Boolean =
  (modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0

private fun MouseEvent.isAltPressed(): Boolean =
  (modifiersEx and InputEvent.ALT_DOWN_MASK) != 0