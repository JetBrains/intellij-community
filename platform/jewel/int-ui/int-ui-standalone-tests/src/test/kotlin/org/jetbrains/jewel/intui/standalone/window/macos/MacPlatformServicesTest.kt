// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

import androidx.compose.ui.awt.ComposeWindow
import java.awt.Window
import java.lang.foreign.MemorySegment
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.jewel.intui.standalone.window.macos.MacPlatformServicesDefaultImpl.callMac
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.condition.DisabledIf
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/**
 * Regression test for JEWEL-1388: resolving the native `NSWindow*` must not require `sun.misc.Unsafe` or reflection
 * into JDK-internal `sun.awt`/`sun.lwawt.macosx` classes.
 */
internal class MacPlatformServicesTest {
    @Test
    fun `getWindowFromJavaWindow returns NIL for a null window`() {
        assertEquals(
            expected = ObjcObject(MemorySegment.NULL),
            actual = MacPlatformServicesDefaultImpl.getWindowFromJavaWindow(null),
        )
    }

    @Test
    @DisabledIf(HEADLESS)
    fun `getWindowFromJavaWindow returns NIL for a non-Compose AWT window`() {
        val frame = JFrame()
        try {
            assertEquals(ObjcObject(MemorySegment.NULL), MacPlatformServicesDefaultImpl.getWindowFromJavaWindow(frame))
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
            val handleMemoryAddress = MemorySegment.ofAddress(handle)

            assertNotEquals(0L, handle, "A displayed ComposeWindow must expose a native window handle")
            assertEquals(
                ObjcObject(handleMemoryAddress),
                MacPlatformServicesDefaultImpl.getWindowFromJavaWindow(window),
            )
        } finally {
            SwingUtilities.invokeAndWait { window.dispose() }
        }
    }

    @Test
    fun `callMac should return null if block throws`() {
        val result = callMac { error("all your base are belong to us") }

        assertNull(result)
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `callMac should return value if no exceptions are thrown`() {
        val powerfulResult = 42
        val result = callMac { powerfulResult }

        assertEquals(powerfulResult, result)
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `onPreferencesChanged calls the listener when the scroller style changes`() {
        var changedPreferences = 0
        MacPlatformServicesDefaultImpl.onPreferencesChanged { changedPreferences++ }.use { postScrollerStyleChange() }

        assertEquals(1, changedPreferences)
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `onPreferencesChanged calls every registered listener`() {
        var firstCalls = 0
        var secondCalls = 0
        MacPlatformServicesDefaultImpl.onPreferencesChanged { firstCalls++ }
            .use {
                MacPlatformServicesDefaultImpl.onPreferencesChanged { secondCalls++ }.use { postScrollerStyleChange() }
            }

        assertEquals(1, firstCalls)
        assertEquals(1, secondCalls)
    }

    @Test
    @EnabledOnOs(OS.MAC)
    fun `a closed listener is not called`() {
        var changedPreferences = 0
        val subscription = MacPlatformServicesDefaultImpl.onPreferencesChanged { changedPreferences++ }
        subscription.close()
        subscription.close()

        postScrollerStyleChange()

        assertEquals(0, changedPreferences)
    }

    @Test
    @DisabledOnOs(OS.MAC)
    fun `onPreferencesChanged returns a handle that does nothing outside macOS`() {
        MacPlatformServicesDefaultImpl.onPreferencesChanged { error("Must not be called") }.close()
    }

    /** Posts the scroller style notification, then waits until the EDT has run the listeners. */
    private fun postScrollerStyleChange() {
        withAutoreleasePool {
            postNotification(getDefaultCenter(), nsString("NSPreferredScrollerStyleDidChangeNotification"))
        }
        SwingUtilities.invokeAndWait {}
    }

    @Test
    @DisabledOnOs(OS.MAC)
    @DisabledIf(HEADLESS)
    fun `updateFullScreenButtons does nothing outside macOS`() {
        assertNoUncaughtEdtException { window -> MacPlatformServicesDefaultImpl.updateFullScreenButtons(window) }
    }

    @Test
    @DisabledOnOs(OS.MAC)
    @DisabledIf(HEADLESS)
    fun `updateColors does nothing outside macOS`() {
        assertNoUncaughtEdtException { window -> MacPlatformServicesDefaultImpl.updateColors(window) }
    }

    @Test
    @DisabledOnOs(OS.MAC)
    fun `hideCursorUntilMoved does nothing outside macOS`() {
        // The call is synchronous, so an exception from the Objective-C runtime fails the test directly.
        MacPlatformServicesDefaultImpl.hideCursorUntilMoved()
    }

    private fun assertNoUncaughtEdtException(call: (Window) -> Unit) {
        val failures = mutableListOf<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> failures += throwable }
        val frame = JFrame()
        try {
            call(frame)
            // The call posts its native work with invokeLater, so wait until the EDT has run it.
            SwingUtilities.invokeAndWait {}
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
            frame.dispose()
        }

        assertTrue(failures.isEmpty(), "Expected no exception on the EDT, but got: $failures")
    }

    private companion object {
        const val HEADLESS = "java.awt.GraphicsEnvironment#isHeadless"
    }
}
