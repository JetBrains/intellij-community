// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.shortcut

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi

@Suppress("DEPRECATION")
private const val REPRESENTABLE_SWING_MODIFIERS_MASK: Int =
    InputEvent.CTRL_DOWN_MASK or
        InputEvent.SHIFT_DOWN_MASK or
        InputEvent.ALT_DOWN_MASK or
        InputEvent.META_DOWN_MASK or
        InputEvent.CTRL_MASK or
        InputEvent.SHIFT_MASK or
        InputEvent.ALT_MASK or
        InputEvent.META_MASK

/**
 * The [JewelKeyStroke] for a Swing [keyStroke], or `null` when the stroke has no mappable key code, is key-release
 * triggered, or carries modifier bits [JewelKeyStroke] cannot represent (such as AltGraph or mouse-button masks). Used
 * by both menu scopes and the bridge keymap lookup so Swing-to-Compose conversion lives in one place.
 */
@ApiStatus.Internal
@InternalJewelApi
public fun swingKeyStrokeToJewelKeyStroke(keyStroke: KeyStroke): JewelKeyStroke? {
    if (keyStroke.keyCode == KeyEvent.VK_UNDEFINED) return null
    if (keyStroke.isOnKeyRelease) return null
    if ((keyStroke.modifiers and REPRESENTABLE_SWING_MODIFIERS_MASK.inv()) != 0) return null
    return JewelKeyStroke(
        key = Key(keyStroke.keyCode).canonicalShortcutKey(),
        ctrl = keyStroke.modifiers and InputEvent.CTRL_DOWN_MASK != 0,
        shift = keyStroke.modifiers and InputEvent.SHIFT_DOWN_MASK != 0,
        alt = keyStroke.modifiers and InputEvent.ALT_DOWN_MASK != 0,
        meta = keyStroke.modifiers and InputEvent.META_DOWN_MASK != 0,
    )
}

/** The Swing press [KeyStroke] for a [JewelKeyStroke], matching AWT key code and modifier masks. */
@ApiStatus.Internal
@InternalJewelApi
public fun JewelKeyStroke.toSwingKeyStroke(): KeyStroke {
    var modifiers = 0
    if (ctrl) modifiers = modifiers or InputEvent.CTRL_DOWN_MASK
    if (shift) modifiers = modifiers or InputEvent.SHIFT_DOWN_MASK
    if (alt) modifiers = modifiers or InputEvent.ALT_DOWN_MASK
    if (meta) modifiers = modifiers or InputEvent.META_DOWN_MASK
    return KeyStroke.getKeyStroke(key.nativeKeyCode, modifiers)
}

/**
 * The Compose representation of an AWT key event, for host integrations that sit at AWT dispatch stages (the IJPL
 * bridge's claim delivery, tests). Mirrors Compose Desktop's own internal conversion: the `KeyEvent(nativeKeyEvent)`
 * constructor must NOT be called with a raw AWT event — the desktop accessors require the internal representation and
 * throw on anything else.
 *
 * Raw AWT modifiers, `keyChar`, and `nativeEvent` are preserved verbatim so OS-aware AltGr classification lives in one
 * place ([JewelKeyStroke.fromKeyDownOrNull]).
 */
@ApiStatus.Internal
@InternalJewelApi
@OptIn(InternalComposeUiApi::class)
public fun KeyEvent.toComposeKeyEvent(): ComposeKeyEvent =
    ComposeKeyEvent(
        key =
            Key(
                nativeKeyCode = keyCode,
                nativeKeyLocation =
                    if (keyLocation == KeyEvent.KEY_LOCATION_UNKNOWN) KeyEvent.KEY_LOCATION_STANDARD else keyLocation,
            ),
        type =
            when (id) {
                KeyEvent.KEY_PRESSED -> KeyEventType.KeyDown
                KeyEvent.KEY_RELEASED -> KeyEventType.KeyUp
                else -> KeyEventType.Unknown
            },
        codePoint = keyChar.code,
        isCtrlPressed = isControlDown,
        isMetaPressed = isMetaDown,
        isAltPressed = isAltDown,
        isShiftPressed = isShiftDown,
        nativeEvent = this,
    )
