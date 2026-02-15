package org.jetbrains.jewel.ui.component.interactions

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performKeyInput
import java.awt.event.KeyEvent as AWTKeyEvent

private const val SHIFT_REQUIRED = "_+\":?!@#$%^&*>"
private val CHAR_KEY_MAP = buildMap {
    ('a'..'z').zip(AWTKeyEvent.VK_A..AWTKeyEvent.VK_Z).forEach { (char, code) -> put(char, Key(code)) }
    ('0'..'9').zip(AWTKeyEvent.VK_0..AWTKeyEvent.VK_9).forEach { (char, code) -> put(char, Key(code)) }

    // private const val PUNCTUATION_MARKS = "*_-+\"'/.#$>: ,;?!@%^&"
    // Map common punctuation to their base key codes; Shift will be handled separately for needed chars
    put('-', Key(AWTKeyEvent.VK_MINUS))
    put('_', Key(AWTKeyEvent.VK_MINUS))
    put('+', Key(AWTKeyEvent.VK_EQUALS))
    put('\'', Key(AWTKeyEvent.VK_QUOTE))
    put('"', Key(AWTKeyEvent.VK_QUOTE))
    put('/', Key(AWTKeyEvent.VK_SLASH))
    put('?', Key(AWTKeyEvent.VK_SLASH))
    put('.', Key(AWTKeyEvent.VK_PERIOD))
    put('>', Key(AWTKeyEvent.VK_PERIOD))
    put(',', Key(AWTKeyEvent.VK_COMMA))
    put(';', Key(AWTKeyEvent.VK_SEMICOLON))
    put(':', Key(AWTKeyEvent.VK_SEMICOLON))
    put('#', Key(AWTKeyEvent.VK_3))
    put('$', Key(AWTKeyEvent.VK_4))
    put('!', Key(AWTKeyEvent.VK_1))
    put('@', Key(AWTKeyEvent.VK_2))
    put('%', Key(AWTKeyEvent.VK_5))
    put('^', Key(AWTKeyEvent.VK_6))
    put('&', Key(AWTKeyEvent.VK_7))
    put('*', Key(AWTKeyEvent.VK_8))

    put(' ', Key.Spacebar)
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.performKeyPress(
    key: Key,
    ctrl: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false,
    meta: Boolean = false,
    duration: Long = 50L,
    rule: ComposeTestRule? = null,
): SemanticsNodeInteraction = performKeyInput {
    rule?.waitForIdle()
    if (ctrl) keyDown(Key.CtrlLeft)
    if (shift) keyDown(Key.ShiftLeft)
    if (alt) keyDown(Key.AltLeft)
    if (meta) keyDown(Key.MetaLeft)

    keyDown(key)
    advanceEventTime(duration)
    keyUp(key)

    if (meta) keyUp(Key.MetaLeft)
    if (alt) keyUp(Key.AltLeft)
    if (shift) keyUp(Key.ShiftLeft)
    if (ctrl) keyUp(Key.CtrlLeft)
    rule?.waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.performKeyPress(
    letter: Char,
    duration: Long = 50L,
    rule: ComposeTestRule? = null,
): SemanticsNodeInteraction {
    rule?.waitForIdle()
    val result =
        performKeyPress(
            key = CHAR_KEY_MAP.getOrDefault(letter.lowercaseChar(), Key(AWTKeyEvent.VK_UNDEFINED)),
            shift = letter.isUpperCase() || SHIFT_REQUIRED.contains(letter),
            duration = duration,
            rule = rule,
        )
    rule?.waitForIdle()
    return result
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.performKeyPress(
    text: String,
    duration: Long = 50L,
    rule: ComposeTestRule? = null,
): SemanticsNodeInteraction {
    rule?.waitForIdle()
    val result = text.toCharArray().fold(this) { acc, c -> acc.performKeyPress(c, duration, rule) }
    rule?.waitForIdle()
    return result
}
