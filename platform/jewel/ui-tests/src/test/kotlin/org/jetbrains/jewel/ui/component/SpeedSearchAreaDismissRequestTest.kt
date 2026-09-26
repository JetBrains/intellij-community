// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalJewelApi::class)
class SpeedSearchAreaDismissRequestTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `suppresses outside dismissal when dismiss on lose focus is disabled`() {
        val renderer = recordSpeedSearchPopup(dismissOnLoseFocus = false)

        assertTrue(renderer.properties.isNotEmpty(), "The popup was never rendered")
        assertFalse(renderer.properties.last().dismissOnClickOutside, "Outside dismissal should be suppressed")
    }

    @Test
    fun `allows outside dismissal when dismiss on lose focus is enabled`() {
        val renderer = recordSpeedSearchPopup(dismissOnLoseFocus = true)

        assertTrue(renderer.properties.last().dismissOnClickOutside, "Outside dismissal should be allowed")
    }

    @Test
    fun `keeps escape enabled regardless of dismiss on lose focus`() {
        for (dismissOnLoseFocus in listOf(false, true)) {
            val renderer = recordSpeedSearchPopup(dismissOnLoseFocus = dismissOnLoseFocus)

            assertTrue(
                renderer.properties.last().dismissOnBackPress,
                "Escape must always hide the search input, whatever the outside-dismissal policy is",
            )
            assertTrue(renderer.dismissRequests.last() != null, "Speed search must always be able to hide its input")
        }
    }

    private fun recordSpeedSearchPopup(dismissOnLoseFocus: Boolean): RecordingPopupRenderer {
        val renderer = RecordingPopupRenderer()
        var speedSearchState: SpeedSearchState? = null
        val oldUseCustomPopupRenderer = JewelFlags.useCustomPopupRenderer
        JewelFlags.useCustomPopupRenderer = true
        try {
            composeRule.setContent {
                IntUiTheme {
                    CompositionLocalProvider(LocalPopupRenderer provides renderer) {
                        val state = rememberSpeedSearchState()
                        speedSearchState = state

                        SpeedSearchArea(state = state, dismissOnLoseFocus = dismissOnLoseFocus) {
                            Text("Searchable content")
                        }
                    }
                }
            }
            composeRule.runOnIdle { speedSearchState?.isVisible = true }
            composeRule.waitForIdle()
        } finally {
            JewelFlags.useCustomPopupRenderer = oldUseCustomPopupRenderer
        }
        return renderer
    }
}
