// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.popup

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.awt.Component
import java.awt.Container
import java.awt.event.KeyEvent as AWTKeyEvent

internal fun Component.findPopupKeyEventTarget(): Component = findPopupKeyEventTargetOrNull() ?: this

internal fun Component.dispatchPopupKeyEventToTargetListeners(event: AWTKeyEvent) {
    keyListeners.forEach { listener ->
        when (event.id) {
            AWTKeyEvent.KEY_PRESSED -> listener.keyPressed(event)
            AWTKeyEvent.KEY_RELEASED -> listener.keyReleased(event)
            AWTKeyEvent.KEY_TYPED -> listener.keyTyped(event)
        }
    }
}

private fun Component.findPopupKeyEventTargetOrNull(): Component? {
    if (keyListeners.isNotEmpty()) return this
    if (this is Container) {
        components.forEach { child ->
            val target = child.findPopupKeyEventTargetOrNull()
            if (target != null) return target
        }
    }
    return null
}

internal fun isDismissRequestKey(type: KeyEventType, key: Key, hasModifier: Boolean = false): Boolean =
    type == KeyEventType.KeyDown && key == Key.Escape && !hasModifier

internal fun isModifiedDismissRequestKey(type: KeyEventType, key: Key, hasModifier: Boolean): Boolean =
    type == KeyEventType.KeyDown && key == Key.Escape && hasModifier

internal fun KeyEvent.hasDismissKeyModifier(): Boolean =
    isMetaPressed || isCtrlPressed || isAltPressed || isShiftPressed

internal fun KeyEvent.isDismissRequest(): Boolean =
    isDismissRequestKey(type = type, key = key, hasModifier = hasDismissKeyModifier())

internal fun KeyEvent.isModifiedDismissRequest(): Boolean =
    isModifiedDismissRequestKey(type = type, key = key, hasModifier = hasDismissKeyModifier())
