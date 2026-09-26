// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import androidx.compose.ui.awt.ComposeWindow
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

/**
 * Waits until a test application has published its window.
 *
 * The first window of a JVM pays for JVM startup, Skiko's native loading and the GL probe with its fallback. On a cold
 * CI runner that has taken longer than ten seconds (JEWEL-1441). Nothing else runs while a test waits here, so a large
 * budget costs nothing on a warm machine.
 */
internal suspend fun AtomicReference<ComposeWindow?>.awaitWindow(timeout: Duration = 60.seconds): ComposeWindow {
    val start = TimeSource.Monotonic.markNow()
    while (start.elapsedNow() < timeout) {
        get()?.let {
            return it
        }
        delay(100.milliseconds)
    }
    error("The Compose test window was not created within $timeout")
}
