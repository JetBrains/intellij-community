// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import com.intellij.util.ui.MacUIUtil
import org.jetbrains.jewel.ui.platform.PlatformCursorController
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

/**
 * Bridge implementation of [PlatformCursorController] for macOS.
 *
 * This implementation simply invokes the [MacUIUtil.hideCursor] to properly hide the cursor while typing on macOS. The
 * cursor will stay hidden until the next mouse movement.
 *
 * @see com.intellij.util.ui.MacUIUtil.hideCursor
 */
internal object BridgePlatformCursorController : PlatformCursorController {
    override fun hideCursor() {
        if (hostOs == OS.MacOS) {
            MacUIUtil.hideCursor()
        }
    }
}
