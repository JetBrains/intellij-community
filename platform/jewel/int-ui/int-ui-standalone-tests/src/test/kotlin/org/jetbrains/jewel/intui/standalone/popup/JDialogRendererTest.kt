// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.popup

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupProperties
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.defaults
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.ui.component.styling.MenuItemColors
import org.jetbrains.jewel.ui.component.styling.MenuMetrics
import org.jetbrains.jewel.ui.component.styling.SubmenuMetrics
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

    @Test
    fun `submenu defaults offset matches menu content padding`() {
        val contentPadding = MenuMetrics.defaults().contentPadding
        val offset = SubmenuMetrics.defaults().offset
        assertEquals(contentPadding.calculateLeftPadding(LayoutDirection.Ltr), offset.x)
        assertEquals(contentPadding.calculateRightPadding(LayoutDirection.Ltr), offset.x)
        assertEquals(-contentPadding.calculateTopPadding(), offset.y)
    }

    @Test
    fun `popupBoundsToAwt preserves owner alignment and eliminates fractional drift`() {
        for ((scale, ownerY) in listOf(1.0f to 100, 1.25f to 101, 1.5f to 101, 1.75f to 103, 2.0f to 100)) {
            val ownerOnDisplay = Point(100, ownerY)
            val ownerOnScreen = Point(100, ownerY)
            val ownerPxX = Math.round(ownerOnDisplay.x * scale)
            val ownerPxY = Math.round(ownerOnDisplay.y * scale)
            val aligned =
                popupBoundsToAwt(
                    popupRectPx = Rectangle(ownerPxX, ownerPxY, 100, 50),
                    ownerLocationOnDisplay = ownerOnDisplay,
                    ownerLocationOnScreen = ownerOnScreen,
                    density = scale,
                )
            assertEquals(ownerY, aligned.y, "Y alignment mismatch at scale=$scale, ownerY=$ownerY")
            assertEquals(100, aligned.x, "X alignment mismatch at scale=$scale")
        }
    }

    @Test
    fun `popupBoundsToAwt aligns right-opening and left-flipped odd-pixel submenus flush with parent`() {
        val cases = listOf(Triple(2.0f, 179, 185), Triple(1.25f, 113, 181), Triple(1.5f, 136, 136))
        for ((scale, parentWidthPx, childWidthPx) in cases) {
            val ownerOnDisplay = Point(72, 160)
            val ownerOnScreen = Point(72, 160)
            val ownerPxX = Math.round(ownerOnDisplay.x * scale)
            val ownerPxY = Math.round(ownerOnDisplay.y * scale)

            val parentAwt =
                popupBoundsToAwt(
                    popupRectPx = Rectangle(ownerPxX, ownerPxY, parentWidthPx, 121),
                    ownerLocationOnDisplay = ownerOnDisplay,
                    ownerLocationOnScreen = ownerOnScreen,
                    density = scale,
                )
            val rightChildAwt =
                popupBoundsToAwt(
                    popupRectPx = Rectangle(ownerPxX + parentWidthPx, ownerPxY, childWidthPx, 121),
                    ownerLocationOnDisplay = ownerOnDisplay,
                    ownerLocationOnScreen = ownerOnScreen,
                    density = scale,
                )
            assertEquals(
                parentAwt.x + parentAwt.width,
                rightChildAwt.x,
                "Right child gap/overlap at scale=$scale, parentWidthPx=$parentWidthPx",
            )
            assertEquals(parentAwt.y, rightChildAwt.y, "Right child Y mismatch at scale=$scale")

            val leftChildAwt =
                popupBoundsToAwt(
                    popupRectPx = Rectangle(ownerPxX - childWidthPx, ownerPxY, childWidthPx, 121),
                    ownerLocationOnDisplay = ownerOnDisplay,
                    ownerLocationOnScreen = ownerOnScreen,
                    density = scale,
                )
            assertEquals(
                parentAwt.x,
                leftChildAwt.x + leftChildAwt.width,
                "Left child gap/overlap at scale=$scale, childWidthPx=$childWidthPx",
            )
            assertEquals(parentAwt.y, leftChildAwt.y, "Left child Y mismatch at scale=$scale")
        }
    }

    @Test
    fun `popupBoundsToAwt preserves negative secondary monitor screen coordinates`() {
        val ownerOnDisplay = Point(100, 100)
        val ownerOnScreen = Point(-1820, 100)
        val bounds =
            popupBoundsToAwt(
                popupRectPx = Rectangle(200 + 179, 200, 185, 121),
                ownerLocationOnDisplay = ownerOnDisplay,
                ownerLocationOnScreen = ownerOnScreen,
                density = 2.0f,
            )
        assertEquals(Rectangle(-1731, 100, 92, 60), bounds)
    }
}
