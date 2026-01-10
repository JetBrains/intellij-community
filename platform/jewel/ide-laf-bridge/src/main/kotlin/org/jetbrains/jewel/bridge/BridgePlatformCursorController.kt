// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import com.intellij.util.ui.MacUIUtil
import org.jetbrains.jewel.ui.platform.PlatformCursorController
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

internal object BridgePlatformCursorController : PlatformCursorController {
    override fun hideCursorWhileTyping() {
        if (hostOs == OS.MacOS) {
            MacUIUtil.hideCursor()
        }
    }
}
