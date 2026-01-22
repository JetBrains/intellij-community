// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.util

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.text.TextRange
import java.awt.event.KeyEvent as AWTKeyEvent
import org.jetbrains.skiko.hostOs

internal fun TextFieldState.handleKeyEvent(
    event: KeyEvent,
    onTextChange: (CharSequence) -> Unit = {},
    allowNavigationWithArrowKeys: Boolean = true,
    allowedSymbols: String? = null,
): Boolean =
    when {
        event.type != KeyEventType.KeyDown -> false // Only handle key down events
        text.isNotEmpty() && event.key.nativeKeyCode in navigationKeyEvents ->
            handleTextNavigationKeys(event, allowNavigationWithArrowKeys)
        event.key == Key.Delete -> handleDeleteKeyInput(event, onTextChange)
        event.key == Key.Backspace -> handleBackspaceKeyInput(event, onTextChange)
        event.key == Key.Spacebar && text.isBlank() -> false
        !event.isReallyTypedEvent() -> false // Only handle printable keys
        else -> handleValidKeyInput(event, allowedSymbols, onTextChange)
    }

internal fun TextFieldState.handleTextNavigationKeys(event: KeyEvent, allowNavigationWithArrowKeys: Boolean): Boolean =
    when (event.key.nativeKeyCode) {
        AWTKeyEvent.VK_RIGHT ->
            if (event.isAltPressed) {
                edit { placeCursorAtEnd() }
                true
            } else if (allowNavigationWithArrowKeys) {
                edit { selection = TextRange((selection.end + 1).coerceIn(0 until length)) }
                true
            } else {
                false
            }
        AWTKeyEvent.VK_LEFT ->
            if (event.isAltPressed) {
                edit { placeCursorBeforeCharAt(0) }
                true
            } else if (allowNavigationWithArrowKeys) {
                edit { selection = TextRange((selection.start - 1).coerceIn(0 until length)) }
                true
            } else {
                false
            }
        else -> false
    }

private fun TextFieldState.handleDeleteKeyInput(event: KeyEvent, onTextChange: (CharSequence) -> Unit): Boolean =
    when {
        text.isEmpty() -> false
        event.isAltPressed -> {
            setTextAndPlaceCursorAtEnd("")
            onTextChange(text)
            true
        }
        selection.end < text.length -> false
        else -> {
            edit {
                if (selection.start != selection.end) {
                    delete(selection.min, selection.max)
                } else if (selection.end < length) {
                    delete(selection.end, selection.end + 1)
                }
            }

            onTextChange(text)

            true
        }
    }

private fun TextFieldState.handleBackspaceKeyInput(event: KeyEvent, onTextChange: (CharSequence) -> Unit): Boolean =
    when {
        text.isEmpty() -> false
        event.isAltPressed -> {
            setTextAndPlaceCursorAtEnd("")
            onTextChange(text)
            true
        }
        selection.start < 0 -> false
        selection.start == 0 && selection.start == selection.end -> false
        else -> {
            edit {
                if (selection.start != selection.end) {
                    delete(selection.min, selection.max)
                } else if (selection.end > 0) {
                    delete(selection.start - 1, selection.start)
                }
            }

            onTextChange(text)

            true
        }
    }

private fun TextFieldState.handleValidKeyInput(
    event: KeyEvent,
    allowedSymbols: String?,
    onTextChange: (CharSequence) -> Unit,
): Boolean {
    val char = event.toChar()

    if (!char.isLetterOrDigit() && allowedSymbols != null && !allowedSymbols.contains(char)) {
        return false
    }

    edit {
        if (selection.start != selection.end) {
            replace(selection.min, selection.max, char.toString())
        } else {
            append(char.toString())
        }
    }

    onTextChange(text)

    return true
}

/**
 * **Swing Version:**
 * [UIUtil.isReallyTypedEvent](https://github.com/JetBrains/intellij-community/blob/master/platform/util/ui/src/com/intellij/util/ui/UIUtil.java)
 */
private fun KeyEvent.isReallyTypedEvent(): Boolean {
    val keyChar = toChar()
    val code = keyChar.code

    return when {
        // Ignoring undefined characters
        keyChar == AWTKeyEvent.CHAR_UNDEFINED -> {
            false
        }

        // Handling non-printable chars (e.g. Tab, Enter, Delete, etc.)
        keyChar.code < 0x20 || keyChar.code == 0x7F -> {
            false
        }

        // Allow input of special characters on Windows in Persian keyboard layout using Ctrl+Shift+1..4
        hostOs.isWindows && code >= 0x200C && code <= 0x200D -> {
            true
        }
        hostOs.isMacOS -> {
            !isMetaPressed && !isCtrlPressed
        }
        else -> {
            !isAltPressed && !isCtrlPressed
        }
    }
}

private fun KeyEvent.toChar(): Char =
    when (key) {
        Key.Spacebar -> ' '
        else -> utf16CodePoint.toChar()
    }

private val navigationKeyEvents = setOf(AWTKeyEvent.VK_LEFT, AWTKeyEvent.VK_RIGHT)
