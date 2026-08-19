// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

import androidx.compose.ui.awt.ComposeWindow
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIf

/**
 * Regression test for JEWEL-1388: resolving the native `NSWindow*` must not require `sun.misc.Unsafe` or reflection
 * into JDK-internal `sun.awt`/`sun.lwawt.macosx` classes.
 */
internal class MacPlatformServicesWindowHandleTest {
    @Test
    fun `getWindowFromJavaWindow returns NIL for a null window`() {
        assertEquals(ID.NIL, MacPlatformServicesDefaultImpl.getWindowFromJavaWindow(null))
    }

    @Test
    @DisabledIf(HEADLESS)
    fun `getWindowFromJavaWindow returns NIL for a non-Compose AWT window`() {
        val frame = JFrame()
        try {
            assertEquals(ID.NIL, MacPlatformServicesDefaultImpl.getWindowFromJavaWindow(frame))
        } finally {
            frame.dispose()
        }
    }

    @Test
    @DisabledIf(HEADLESS)
    fun `getWindowFromJavaWindow returns the native handle of a realized ComposeWindow`() {
        lateinit var window: ComposeWindow
        try {
            var handle = 0L
            SwingUtilities.invokeAndWait {
                window = ComposeWindow()
                window.isVisible = true
                handle = window.windowHandle
            }

            assertNotEquals(0L, handle, "A displayed ComposeWindow must expose a native window handle")
            assertEquals(ID(handle), MacPlatformServicesDefaultImpl.getWindowFromJavaWindow(window))
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }

    private companion object {
        const val HEADLESS = "java.awt.GraphicsEnvironment#isHeadless"
    }
}
