// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Controller for platform-specific cursor behavior.
 *
 * This interface provides methods to control cursor visibility and behavior in a platform-specific way. Implementations
 * should handle platform differences internally.
 */
public interface PlatformCursorController {
    /**
     * The cursor stays hidden until the next mouse movement.
     *
     * This is a macOS-only behavior. The cursor remains visible on Windows and Linux.
     */
    public fun hideCursor()
}

public val LocalPlatformCursorController: ProvidableCompositionLocal<PlatformCursorController> =
    staticCompositionLocalOf {
        error("No LocalPlatformCursorController provided. Have you forgotten the theme?")
    }
