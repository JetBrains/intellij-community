// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.popup

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.window.PopupProperties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.ui.component.styling.MenuItemColors
import org.junit.jupiter.api.Test

internal class JDialogRendererTest {
    @Test
    fun `menu item pressed background matches focused background in light and dark themes`() {
        val lightColors = MenuItemColors.light()
        assertEquals(lightColors.backgroundFocused, lightColors.backgroundPressed)

        val darkColors = MenuItemColors.dark()
        assertEquals(darkColors.backgroundFocused, darkColors.backgroundPressed)
    }

    @Test
    fun `focusable popup is rendered as JDialog`() {
        val properties = PopupProperties(focusable = true)
        assertEquals(PopupWindowKind.Dialog, popupWindowKind(properties))
    }

    @Test
    fun `non-focusable popup is rendered as JWindow`() {
        val properties = PopupProperties(focusable = false)
        assertEquals(PopupWindowKind.Window, popupWindowKind(properties))
    }

    @Test
    fun `escape key down is dismiss request`() {
        assertTrue(isDismissRequestKey(type = KeyEventType.KeyDown, key = Key.Escape))
    }

    @Test
    fun `escape key up is not dismiss request`() {
        assertFalse(isDismissRequestKey(type = KeyEventType.KeyUp, key = Key.Escape))
    }

    @Test
    fun `modified escape is not dismiss request`() {
        assertFalse(isDismissRequestKey(type = KeyEventType.KeyDown, key = Key.Escape, hasModifier = true))
    }

    @Test
    fun `non-escape key is not dismiss request`() {
        assertFalse(isDismissRequestKey(type = KeyEventType.KeyDown, key = Key.Enter))
    }

    @Test
    fun `modified escape key down is modified dismiss request`() {
        assertTrue(isModifiedDismissRequestKey(type = KeyEventType.KeyDown, key = Key.Escape, hasModifier = true))
    }

    @Test
    fun `modified escape key up is not modified dismiss request`() {
        assertFalse(isModifiedDismissRequestKey(type = KeyEventType.KeyUp, key = Key.Escape, hasModifier = true))
    }
}
