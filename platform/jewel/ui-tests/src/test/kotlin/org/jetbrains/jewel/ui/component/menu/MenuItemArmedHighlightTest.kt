// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.IsHoveredKey
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.junit.Rule
import org.junit.Test

/**
 * Swing arms exactly one menu item at a time. Jewel gets there through [JewelTheme.isSwingCompatMode]: with it on,
 * `chooseValue` ignores hover and only the focused item is armed; with it off, hover outranks focus, so an item under
 * the pointer stays armed while the keyboard arms another one.
 *
 * These tests pin that difference at the menu level, since the pointer/keyboard interplay is not visible from
 * `chooseValue` alone.
 */
class MenuItemArmedHighlightTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `with swing compat off the hovered item stays armed after the keyboard arms another`() {
        composeRule.setContent { IntUiTheme(swingCompatMode = false) { Menu() } }
        hoverSecondItemThenPressDown()

        val unarmed = composeRule.onNodeWithText("Item 1").backgroundColor()

        assertNotEquals(unarmed, composeRule.onNodeWithText("Item 2").backgroundColor(), "hovered item is armed")
        assertNotEquals(unarmed, composeRule.onNodeWithText("Item 3").backgroundColor(), "focused item is armed")
    }

    @Test
    fun `with swing compat on only the keyboard armed item is highlighted`() {
        composeRule.setContent { IntUiTheme(swingCompatMode = true) { Menu() } }
        hoverSecondItemThenPressDown()

        val unarmed = composeRule.onNodeWithText("Item 1").backgroundColor()

        assertEquals(unarmed, composeRule.onNodeWithText("Item 2").backgroundColor(), "hovered item is not armed")
        assertNotEquals(unarmed, composeRule.onNodeWithText("Item 3").backgroundColor(), "focused item is armed")
    }

    /**
     * Leaves the pointer on Item 2 while the keyboard moves the arm on to Item 3, then asserts the resulting semantics.
     * They come out the same in both compat modes, which is why the tests above have to read pixels, since only the
     * colour `chooseValue` picks differs, and that never reaches the semantics tree.
     */
    private fun hoverSecondItemThenPressDown() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Item 2").performMouseInput { enter(center) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("container").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Item 2").assert(SemanticsMatcher.expectValue(IsHoveredKey, true))
        composeRule.onNodeWithText("Item 2").assertIsNotFocused()
        composeRule.onNodeWithText("Item 3").assert(SemanticsMatcher.expectValue(IsHoveredKey, false))
        composeRule.onNodeWithText("Item 3").assertIsFocused()
    }

    private fun SemanticsNodeInteraction.backgroundColor(): Color {
        val pixels = captureToImage().toPixelMap()
        val histogram = mutableMapOf<Color, Int>()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                histogram.merge(pixels[x, y], 1, Int::plus)
            }
        }
        return histogram.maxBy { it.value }.key
    }

    @Composable
    private fun Menu() {
        Box(modifier = Modifier.size(400.dp, 300.dp).testTag("container")) {
            PopupMenu(onDismissRequest = { true }, horizontalAlignment = Alignment.Start) {
                selectableItem(selected = false, onClick = {}) { Text("Item 1") }
                selectableItem(selected = false, onClick = {}) { Text("Item 2") }
                selectableItem(selected = false, onClick = {}) { Text("Item 3") }
            }
        }
    }
}
