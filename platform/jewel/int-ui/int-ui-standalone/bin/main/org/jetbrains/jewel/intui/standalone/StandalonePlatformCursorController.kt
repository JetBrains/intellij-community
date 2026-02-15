// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.jetbrains.jewel.ui.platform.PlatformCursorController
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

/**
 * Standalone implementation of [PlatformCursorController] for macOS.
 *
 * This implementation uses JNA to call the native Objective-C method `[NSCursor setHiddenUntilMouseMoves:]`. The
 * implementation is based on the platform code in `com.intellij.util.ui.MacUIUtil.hideCursor()`, but uses JNA's
 * `objc_msgSend` directly instead of the IJ Foundation framework, since IJ APIs are not available in standalone mode.
 * the Foundation framework, since Foundation is not available in standalone mode.
 *
 * @see com.intellij.util.ui.MacUIUtil.hideCursor
 */
internal object StandalonePlatformCursorController : PlatformCursorController {
    private val objcLibrary: ObjCLibrary? by lazy {
        try {
            Native.load("objc", ObjCLibrary::class.java)
        } catch (_: Exception) {
            null
        }
    }

    override fun hideCursor() {
        if (hostOs == OS.MacOS) {
            try {
                val lib = objcLibrary ?: return

                val nsCursorClass = lib.objc_getClass("NSCursor")
                if (nsCursorClass == null || Pointer.nativeValue(nsCursorClass) == 0L) return

                val selector = lib.sel_registerName("setHiddenUntilMouseMoves:")
                if (selector == null || Pointer.nativeValue(selector) == 0L) return

                lib.objc_msgSend(nsCursorClass, selector, true)
            } catch (_: Throwable) {
                // Silently fail if native calls fail or if JNA is not available
            }
        }
    }

    @Suppress("ktlint:standard:function-naming")
    private interface ObjCLibrary : Library {
        fun objc_getClass(className: String?): Pointer?

        fun sel_registerName(selectorName: String?): Pointer?

        fun objc_msgSend(receiver: Pointer?, selector: Pointer?, vararg args: Any?)
    }
}
