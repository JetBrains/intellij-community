// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.test.assertNull
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
    fun `passes null dismiss request when dismiss on lose focus is disabled`() {
        val renderer = recordSpeedSearchPopup(dismissOnLoseFocus = false)

        assertTrue(renderer.dismissRequests.isNotEmpty())
        assertNull(renderer.dismissRequests.last())
    }

    @Test
    fun `passes dismiss request when dismiss on lose focus is enabled`() {
        val renderer = recordSpeedSearchPopup(dismissOnLoseFocus = true)

        assertTrue(renderer.dismissRequests.any { it != null })
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

private class RecordingPopupRenderer : PopupRenderer {
    val dismissRequests = mutableListOf<(() -> Unit)?>()

    @Suppress("OVERRIDE_DEPRECATION")
    @Composable
    override fun Popup(
        popupPositionProvider: PopupPositionProvider,
        properties: PopupProperties,
        onDismissRequest: (() -> Unit)?,
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
        cornerSize: CornerSize,
        content: @Composable () -> Unit,
    ) {
        Popup(
            popupPositionProvider = popupPositionProvider,
            properties = properties,
            onDismissRequest = onDismissRequest,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent,
            cornerSize = cornerSize,
            windowShape = null,
            content = content,
        )
    }

    @Composable
    override fun Popup(
        popupPositionProvider: PopupPositionProvider,
        properties: PopupProperties,
        onDismissRequest: (() -> Unit)?,
        onPreviewKeyEvent: ((KeyEvent) -> Boolean)?,
        onKeyEvent: ((KeyEvent) -> Boolean)?,
        cornerSize: CornerSize,
        windowShape: ((IntSize) -> java.awt.Shape)?,
        content: @Composable () -> Unit,
    ) {
        dismissRequests += onDismissRequest
        content()
    }
}
