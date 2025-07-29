// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import java.util.UUID
import org.jetbrains.jewel.foundation.theme.LocalThemeInstanceUuid
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

@Composable
internal fun IntUiTestTheme(
    isDark: Boolean = false,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalThemeInstanceUuid provides UUID.randomUUID()) {
        IntUiTheme(isDark, swingCompatMode, content)
    }
}
