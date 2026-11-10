// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposeWindow
import java.awt.Window
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import javax.swing.SwingUtilities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.intui.standalone.styling.macOs
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.skiko.hostOs

/**
 * Provides macOS-specific platform services for window decoration and system preference observation.
 *
 * Implementations communicate with macOS APIs (via FFM/Objective-C) to apply native window chrome updates and read
 * scrollbar settings from native sources such as NSUserDefaults (track-click behavior) and NSScroller (scroller style).
 */
@ApiStatus.Internal
@InternalJewelApi
public interface MacPlatformServices {
    /** Updates the native window's color scheme to match the current Jewel theme. */
    public fun updateColors(w: Window)

    /** Refreshes the full-screen button state in the native window title bar. */
    public fun updateFullScreenButtons(w: Window)

    /** Hides the system cursor until the next mouse-move event (delegates to `NSCursor`). */
    public fun hideCursorUntilMoved()

    /** Reads the current scrollbar track-click behavior from `NSUserDefaults`. */
    public fun readScrollbarTrackClickBehavior(): TrackClickBehavior

    /** Reads the current scrollbar visibility style from the native `NSScroller` preference. */
    public fun readScrollbarVisibility(): ScrollbarVisibility

    /**
     * Registers [action] to be invoked whenever a scrollbar-related system preference changes.
     *
     * Observes both `NSPreferredScrollerStyleDidChangeNotification` (visibility) and
     * `AppleNoRedisplayAppearancePreferenceChanged` (track-click behavior).
     */
    public fun onPreferencesChanged(action: () -> Unit)
}

/** Default [MacPlatformServices] implementation that uses FFM to invoke native macOS (Objective-C) APIs. */
@ApiStatus.Internal
@InternalJewelApi
public object MacPlatformServicesDefaultImpl : MacPlatformServices {
    private val logger = JewelLogger.getInstance(MacPlatformServicesDefaultImpl::class.java.simpleName)

    /**
     * Returns the native `NSWindow*` backing the given AWT [Window], or [MemorySegment.NULL] if [w] isn't a
     * [ComposeWindow] or the native surface hasn't been realized yet.
     *
     * Uses [ComposeWindow.windowHandle], Compose's own supported accessor, instead of reflecting into JDK-internal
     * `sun.awt`/`sun.lwawt.macosx` classes (see JEWEL-1388).
     */
    internal fun getWindowFromJavaWindow(w: Window?): ObjcObject {
        val windowHandle = (w as? ComposeWindow)?.windowHandle ?: 0L
        if (windowHandle == 0L) return ObjcObject(MemorySegment.NULL)
        return ObjcObject(MemorySegment.ofAddress(windowHandle))
    }

    public override fun updateColors(w: Window) {
        SwingUtilities.invokeLater {
            callMac { sendToWindowDelegateIfSupported(getWindowFromJavaWindow(w), "updateColors") }
        }
    }

    public override fun updateFullScreenButtons(w: Window) {
        SwingUtilities.invokeLater {
            callMac { sendToWindowDelegateIfSupported(getWindowFromJavaWindow(w), "updateFullScreenButtons") }
        }
    }

    override fun hideCursorUntilMoved() {
        setHiddenUntilMouseMoves()
    }

    override fun readScrollbarTrackClickBehavior(): TrackClickBehavior =
        callMac {
            val behavior =
                if (isScrollbarTrackClickBehaviorJumpToSpot()) {
                    TrackClickBehavior.JumpToSpot
                } else {
                    TrackClickBehavior.NextPage
                }
            behavior
        } ?: TrackClickBehavior.JumpToSpot

    override fun readScrollbarVisibility(): ScrollbarVisibility =
        callMac {
            val visibility =
                if (isOverlayScrollerStyle()) {
                    ScrollbarVisibility.WhenScrolling.macOs()
                } else {
                    ScrollbarVisibility.AlwaysVisible.macOs()
                }
            visibility
        } ?: ScrollbarVisibility.AlwaysVisible.default()

    /**
     * Only one listener is supported: production code registers it once, from the lazily created `scrollbarService`. A
     * second call replaces the listener and fails to register a second observer class.
     */
    override fun onPreferencesChanged(action: () -> Unit) {
        // Set before registering, so a notification that arrives right after addObserver finds it.
        listener = action

        callMac {
            val callbackHandle =
                MethodHandles.lookup()
                    .findStatic(
                        MacPlatformServicesDefaultImpl::class.java,
                        "onNotificationReceived",
                        MethodType.methodType(
                            Void.TYPE,
                            MemorySegment::class.java,
                            MemorySegment::class.java,
                            MemorySegment::class.java,
                        ),
                    )
            val selector = selRegisterName("handlePreferenceChanged:")
            val observer = createObserver("JewelScrollbarPreferencesObserver", selector, callbackHandle)

            addObserver(
                getDefaultCenter(),
                observer,
                selector,
                nsString("NSPreferredScrollerStyleDidChangeNotification"),
            )
            addDistributedObserver(
                getDistributedCenter(),
                observer,
                selector,
                nsString("AppleNoRedisplayAppearancePreferenceChanged"),
                NS_NOTIFICATION_SUSPENSION_BEHAVIOR_COALESCE,
            )
        }
    }

    /**
     * Executes [producer] inside a `NSAutoreleasePool` and returns its result, or `null` if the current OS is not macOS
     * or if an exception is thrown (the exception is logged as a warning).
     */
    @Suppress("TooGenericExceptionCaught")
    public fun <T : Any> callMac(producer: () -> T?): T? {
        if (!hostOs.isMacOS) return null

        try {
            return withAutoreleasePool(producer)
        } catch (e: Throwable) {
            logger.warn(e)
        }
        return null
    }

    @Volatile private var listener: (() -> Unit)? = null

    @JvmStatic
    @Suppress("UNUSED_PARAMETER", "SSBasedInspection", "TooGenericExceptionCaught")
    private fun onNotificationReceived(self: MemorySegment, selector: MemorySegment, notification: MemorySegment) {
        runCatching {
            SwingUtilities.invokeLater {
                try {
                    listener?.invoke()
                } catch (e: Throwable) {
                    logger.warn(e)
                }
            }
        }
    }
}

/** [ProvidableCompositionLocal] that provides the active [MacPlatformServices] instance for the current window. */
@get:ApiStatus.Internal
@InternalJewelApi
public val LocalMacPlatformServices: ProvidableCompositionLocal<MacPlatformServices> = staticCompositionLocalOf {
    error("No LocalMacPlatformServicesProvider provided. Have you forgotten the theme?")
}
