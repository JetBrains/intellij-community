// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * A [PopupRenderer] that records the `onDismissRequest` it is handed, and renders nothing.
 *
 * Renderers treat a null `onDismissRequest` as "this popup does not want renderer-driven dismissal", and must then
 * neither dismiss nor consume the key. Components that instead pass a callback which decides whether to dismiss look
 * identical from here, so the renderer swallows Escape while nothing closes: this renderer makes that distinction
 * observable in a headless test.
 *
 * The content is deliberately dropped rather than composed inline. A real popup is its own window, so composing it into
 * the caller's layout instead would let it overlap the component that opened it and swallow the pointer events a hover
 * test depends on.
 */
internal class RecordingPopupRenderer : PopupRenderer {
    val dismissRequests: MutableList<(() -> Unit)?> = mutableListOf()
    val properties: MutableList<PopupProperties> = mutableListOf()

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
        this.properties += properties
    }
}
