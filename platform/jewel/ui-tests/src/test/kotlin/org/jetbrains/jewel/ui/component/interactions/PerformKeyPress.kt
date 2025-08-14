package org.jetbrains.jewel.ui.component.interactions

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performKeyInput

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.performKeyPress(
    key: Key,
    ctrl: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false,
    meta: Boolean = false,
    duration: Long = 50L,
): SemanticsNodeInteraction = performKeyInput {
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
}
