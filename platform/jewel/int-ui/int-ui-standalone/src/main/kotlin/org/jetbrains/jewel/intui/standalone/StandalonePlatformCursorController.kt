// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.intui.standalone.window.macos.Foundation
import org.jetbrains.jewel.intui.standalone.window.macos.MacPlatformServices
import org.jetbrains.jewel.intui.standalone.window.macos.MacPlatformServicesDefaultImpl
import org.jetbrains.jewel.ui.platform.PlatformCursorController
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

/**
 * Standalone implementation of [PlatformCursorController] for macOS.
 *
 * This implementation uses JNA to call the native Objective-C method `[NSCursor setHiddenUntilMouseMoves:]`. The
 * implementation is based on the platform code in `com.intellij.util.ui.MacUIUtil.hideCursor()`, but uses the
 * [Foundation] framework wrapper since IJ APIs are not available in standalone mode.
 *
 * @see com.intellij.util.ui.MacUIUtil.hideCursor
 */
@ApiStatus.Internal
@InternalJewelApi
public class StandalonePlatformCursorController(
    private val macPlatformServices: MacPlatformServices = MacPlatformServicesDefaultImpl
) : PlatformCursorController {
    override fun hideCursor() {
        if (hostOs == OS.MacOS) {
            try {
                macPlatformServices.hideCursorUntilMoved()
            } catch (_: Throwable) {
                // Silently fail if native calls fail or if JNA is not available
            }
        }
    }
}
