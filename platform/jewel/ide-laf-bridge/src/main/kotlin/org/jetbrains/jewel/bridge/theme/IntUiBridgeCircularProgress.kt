// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle

internal fun readCircularProgressStyle(isDark: Boolean) =
    CircularProgressStyle(
        frameTime = 125.milliseconds,
        color =
            retrieveColorOrUnspecified("ProgressIcon.color").takeOrElse {
                if (isDark) Color(0xFF6F737A) else Color(0xFFA8ADBD)
            },
    )
