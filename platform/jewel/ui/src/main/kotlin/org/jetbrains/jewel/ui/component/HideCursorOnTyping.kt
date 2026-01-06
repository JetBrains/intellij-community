// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import org.jetbrains.jewel.ui.platform.LocalPlatformCursorController
import org.jetbrains.jewel.ui.platform.PlatformCursorController

@Composable
public fun Modifier.hideCursorOnTyping(
    cursorController: PlatformCursorController = LocalPlatformCursorController.current
): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key != Key.Escape && event.utf16CodePoint != 0) {
        cursorController.hideCursorWhileTyping()
    }
    false // returning false so the text field can handle the key event properly
}
