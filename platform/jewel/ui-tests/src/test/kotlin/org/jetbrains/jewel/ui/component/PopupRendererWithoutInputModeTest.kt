// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * A [PopupRenderer] may implement only the `Popup` overload whose `onDismissRequest` takes no [InputMode]. Jewel calls
 * the overload that does take one, so its default has to reach that implementation and report a mode on its behalf.
 */
class PopupRendererWithoutInputModeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `a renderer that accepts no input mode is reached, and its dismissals report a pointer gesture`() {
        val renderer = RendererWithoutInputMode()
        var reported: InputMode? = null

        rule.setContent {
            renderer.Popup(
                popupPositionProvider = ZeroPosition,
                properties = PopupProperties(),
                onDismissRequest = { mode: InputMode -> reported = mode },
                onPreviewKeyEvent = null,
                onKeyEvent = null,
                cornerSize = ZeroCornerSize,
            ) {}
        }

        val dismissRequest = requireNotNull(renderer.dismissRequest) { "The implemented overload was never reached" }
        rule.runOnIdle { dismissRequest() }

        assertEquals(InputMode.Touch, reported, "Such a renderer cannot tell a key from a pointer")
    }

    /** Implements only the overload whose `onDismissRequest` takes no [InputMode]. */
    private class RendererWithoutInputMode : PopupRenderer {
        var dismissRequest: (() -> Unit)? = null

        @Deprecated("Implement the overload whose onDismissRequest reports the dismissal InputMode.")
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
            dismissRequest = onDismissRequest
        }
    }

    private object ZeroPosition : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
        ): IntOffset = IntOffset.Zero
    }
}
