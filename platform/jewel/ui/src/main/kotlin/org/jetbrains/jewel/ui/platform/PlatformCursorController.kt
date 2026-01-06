// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

public interface PlatformCursorController {
    /** Hides the mouse cursor while the user is typing. This is a MacOS-only behavior. */
    public fun hideCursorWhileTyping()
}

public object NoOpPlatformCursorController : PlatformCursorController {
    override fun hideCursorWhileTyping() {
        // No-op
    }
}

public val LocalPlatformCursorController: ProvidableCompositionLocal<PlatformCursorController> =
    staticCompositionLocalOf {
        NoOpPlatformCursorController
    }
