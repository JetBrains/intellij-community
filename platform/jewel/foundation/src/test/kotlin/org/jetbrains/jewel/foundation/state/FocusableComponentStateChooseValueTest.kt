// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.jetbrains.jewel.foundation.theme.LocalSwingCompatMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
internal class FocusableComponentStateChooseValueTest {
    @get:Rule val composeRule = createComposeRule()

    private data class ProbeState(
        override val isEnabled: Boolean = true,
        override val isPressed: Boolean = false,
        override val isHovered: Boolean = false,
        override val isFocused: Boolean = false,
        override val isActive: Boolean = false,
    ) : FocusableComponentState

    @Composable
    private fun ProbeState.pick(): String =
        chooseValue(
            normal = "normal",
            disabled = "disabled",
            focused = "focused",
            pressed = "pressed",
            hovered = "hovered",
            active = "active",
        )

    private fun resolve(swingCompat: Boolean, states: Map<String, ProbeState>): Map<String, String> {
        val results = mutableMapOf<String, String>()
        composeRule.setContent {
            CompositionLocalProvider(LocalSwingCompatMode provides swingCompat) {
                for ((label, state) in states) {
                    results[label] = state.pick()
                }
            }
        }
        composeRule.waitForIdle()
        return results
    }

    @Test
    fun `hover wins without focus when swing compat is off`() {
        val results =
            resolve(
                swingCompat = false,
                states =
                    mapOf(
                        "normal" to ProbeState(),
                        "hoveredOnly" to ProbeState(isHovered = true),
                        "focusedOnly" to ProbeState(isFocused = true),
                        "hoveredAndFocused" to ProbeState(isHovered = true, isFocused = true),
                        "activeOnly" to ProbeState(isActive = true),
                        "pressedOnly" to ProbeState(isPressed = true),
                        "hoveredAndActive" to ProbeState(isHovered = true, isActive = true),
                        "pressedAndHovered" to ProbeState(isPressed = true, isHovered = true),
                        "disabledAndHovered" to ProbeState(isEnabled = false, isHovered = true),
                    ),
            )

        assertEquals("normal", results["normal"])
        // The fix: a hovered component uses the hovered value even without focus.
        assertEquals("hovered", results["hoveredOnly"])
        assertEquals("focused", results["focusedOnly"])
        assertEquals("hovered", results["hoveredAndFocused"])
        assertEquals("active", results["activeOnly"])
        assertEquals("pressed", results["pressedOnly"])
        // Hover takes precedence over active; pressed takes precedence over hover.
        assertEquals("hovered", results["hoveredAndActive"])
        assertEquals("pressed", results["pressedAndHovered"])
        // Disabled always wins.
        assertEquals("disabled", results["disabledAndHovered"])
    }

    @Test
    fun `swing compat mode disables the hover and pressed branches`() {
        val results =
            resolve(
                swingCompat = true,
                states =
                    mapOf(
                        "hoveredOnly" to ProbeState(isHovered = true),
                        "pressedOnly" to ProbeState(isPressed = true),
                        "hoveredAndFocused" to ProbeState(isHovered = true, isFocused = true),
                        "focusedOnly" to ProbeState(isFocused = true),
                        "activeOnly" to ProbeState(isActive = true),
                        "disabled" to ProbeState(isEnabled = false),
                    ),
            )

        // In Swing compat mode, hover and pressed are ignored.
        assertEquals("normal", results["hoveredOnly"])
        assertEquals("normal", results["pressedOnly"])
        assertEquals("focused", results["hoveredAndFocused"])
        assertEquals("focused", results["focusedOnly"])
        assertEquals("active", results["activeOnly"])
        assertEquals("disabled", results["disabled"])
    }
}
