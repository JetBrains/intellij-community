// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.LocalPopupRenderer
import org.jetbrains.jewel.ui.component.RecordingPopupRenderer
import org.junit.Rule
import org.junit.Test

/**
 * `GotItTooltip` handles Escape itself, on its anchor rather than inside the popup, so it has to declare that the
 * renderer should keep its hands off the key.
 *
 * Without this, a renderer that honours the default `dismissOnBackPress = true` will dismiss the popup — invoking a
 * callback that does nothing, since the tooltip's visibility is owned by the caller — and consume Escape on the way, so
 * the anchor's own handler never runs and the tooltip stays on screen. That was measured against the IDE bridge
 * renderer, which is the one with no window-ownership check to fall back on.
 */
@OptIn(ExperimentalJewelApi::class)
class GotItTooltipDismissPolicyTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `opts out of renderer-driven escape dismissal`() {
        val renderer = RecordingPopupRenderer()
        val oldUseCustomPopupRenderer = JewelFlags.useCustomPopupRenderer
        JewelFlags.useCustomPopupRenderer = true
        try {
            composeRule.setContent {
                IntUiTheme {
                    CompositionLocalProvider(LocalPopupRenderer provides renderer) {
                        GotItTooltip(text = "Body", visible = true, onDismiss = {}) {}
                    }
                }
            }
            composeRule.waitForIdle()
        } finally {
            JewelFlags.useCustomPopupRenderer = oldUseCustomPopupRenderer
        }

        assertTrue(renderer.properties.isNotEmpty(), "The tooltip popup was never rendered")
        assertFalse(
            renderer.properties.last().dismissOnBackPress,
            "GotItTooltip handles Escape on its anchor, so the renderer must neither dismiss nor consume it",
        )
        assertFalse(
            renderer.properties.last().dismissOnClickOutside,
            "GotItTooltip is dismissed by its own buttons or timeout, not by clicking away",
        )
    }
}
