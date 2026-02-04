// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

import com.sun.jna.Callback
import com.sun.jna.Pointer
import java.awt.Component
import java.awt.Window
import java.lang.reflect.InvocationTargetException
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.SwingUtilities
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.intui.standalone.styling.macOs
import org.jetbrains.jewel.intui.standalone.window.UnsafeAccessing
import org.jetbrains.jewel.intui.standalone.window.accessible
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.skiko.hostOs

@ApiStatus.Internal
@InternalJewelApi
public interface MacPlatformServices {
    public fun hideCursorUntilMoved()

    public fun readScrollbarTrackClickBehavior(): TrackClickBehavior

    public fun readScrollbarVisibility(): ScrollbarVisibility

    public fun onPreferencesChanged(action: () -> Unit)
}

@ApiStatus.Internal
@InternalJewelApi
public object MacPlatformServicesDefaultImpl : MacPlatformServices {
    private val logger = Logger.getLogger(MacPlatformServicesDefaultImpl::class.java.simpleName)
    private var nativeCallbackReference: Callback? = null // Keep a strong reference here to prevent GC

    init {
        try {
            UnsafeAccessing.assignAccessibility(
                UnsafeAccessing.desktopModule,
                listOf("sun.awt", "sun.lwawt", "sun.lwawt.macosx"),
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.log(Level.WARNING, "Assign access for jdk.desktop failed.", e)
        }
    }

    internal fun getWindowFromJavaWindow(w: Window?): ID {
        if (w == null) {
            return ID.NIL
        }
        try {
            val cPlatformWindow = getPlatformWindow(w)
            if (cPlatformWindow != null) {
                val ptr = cPlatformWindow.javaClass.superclass.getDeclaredField("ptr")
                ptr.setAccessible(true)
                return ID(ptr.getLong(cPlatformWindow))
            }
        } catch (e: IllegalAccessException) {
            logger.log(Level.WARNING, "Fail to get cPlatformWindow from awt window.", e)
        } catch (e: NoSuchFieldException) {
            logger.log(Level.WARNING, "Fail to get cPlatformWindow from awt window.", e)
        }
        return ID.NIL
    }

    public fun getPlatformWindow(w: Window): Any? {
        try {
            val awtAccessor = Class.forName("sun.awt.AWTAccessor")
            val componentAccessor = awtAccessor.getMethod("getComponentAccessor").invoke(null)
            val getPeer = componentAccessor.javaClass.getMethod("getPeer", Component::class.java).accessible()
            val peer = getPeer.invoke(componentAccessor, w)
            if (peer != null) {
                val cWindowPeerClass: Class<*> = peer.javaClass
                val getPlatformWindowMethod = cWindowPeerClass.getDeclaredMethod("getPlatformWindow")
                val cPlatformWindow = getPlatformWindowMethod.invoke(peer)
                if (cPlatformWindow != null) {
                    return cPlatformWindow
                }
            }
        } catch (e: NoSuchMethodException) {
            logger.log(Level.WARNING, "Fail to get cPlatformWindow from awt window.", e)
        } catch (e: IllegalAccessException) {
            logger.log(Level.WARNING, "Fail to get cPlatformWindow from awt window.", e)
        } catch (e: InvocationTargetException) {
            logger.log(Level.WARNING, "Fail to get cPlatformWindow from awt window.", e)
        } catch (e: ClassNotFoundException) {
            logger.log(Level.WARNING, "Fail to get cPlatformWindow from awt window.", e)
        }
        return null
    }

    public fun updateColors(w: Window) {
        SwingUtilities.invokeLater {
            val window = getWindowFromJavaWindow(w)
            val delegate = Foundation.invoke(window, "delegate")
            if (
                Foundation.invoke(delegate, "respondsToSelector:", Foundation.createSelector("updateColors"))
                    .booleanValue()
            ) {
                Foundation.invoke(delegate, "updateColors")
            }
        }
    }

    public fun updateFullScreenButtons(w: Window) {
        SwingUtilities.invokeLater {
            val selector = Foundation.createSelector("updateFullScreenButtons")
            val window = getWindowFromJavaWindow(w)
            val delegate = Foundation.invoke(window, "delegate")

            if (Foundation.invoke(delegate, "respondsToSelector:", selector).booleanValue()) {
                Foundation.invoke(delegate, "updateFullScreenButtons")
            }
        }
    }

    override fun hideCursorUntilMoved() {
        val nsCursorClass = Foundation.getObjcClass("NSCursor") ?: return

        val selector = Foundation.createSelector("setHiddenUntilMouseMoves:")
        if (selector == null || Pointer.nativeValue(selector) == 0L) return

        Foundation.invoke(nsCursorClass, selector, true)
    }

    override fun readScrollbarTrackClickBehavior(): TrackClickBehavior =
        callMac {
            // Inspired from MacScrollBarUI
            val userDefaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults")
            Foundation.invoke(userDefaults, "synchronize")
            val isJumpToPage =
                Foundation.invoke(
                        // id =
                        userDefaults,
                        // selector =
                        "boolForKey:",
                        // ...args =
                        Foundation.nsString("AppleScrollerPagingBehavior"),
                    )
                    .booleanValue()

            val behavior =
                if (isJumpToPage) {
                    TrackClickBehavior.JumpToSpot
                } else {
                    TrackClickBehavior.NextPage
                }
            behavior
        } ?: TrackClickBehavior.JumpToSpot

    override fun readScrollbarVisibility(): ScrollbarVisibility =
        callMac {
            // Inspired from MacScrollBarUI
            val isOverlayStyle =
                Foundation.invoke(
                        // id=
                        Foundation.getObjcClass("NSScroller"),
                        // selector=
                        "preferredScrollerStyle",
                    )
                    .booleanValue()

            val visibility =
                if (isOverlayStyle) {
                    ScrollbarVisibility.WhenScrolling.macOs()
                } else {
                    ScrollbarVisibility.AlwaysVisible.macOs()
                }
            visibility
        } ?: ScrollbarVisibility.AlwaysVisible.default()

    @Suppress("UnsafeCallOnNullableType")
    override fun onPreferencesChanged(action: () -> Unit) {
        val callback =
            object : Callback {
                /**
                 * Callback method invoked by macOS NSNotificationCenter when a system preference changes.
                 *
                 * This method is called via Objective-C message dispatch when notifications are posted. It refreshes
                 * both the track click behavior and scrollbar visibility from system preferences, updating the
                 * corresponding state flows to notify observers of the changes.
                 *
                 * @param self The Objective-C receiver object
                 * @param selector The selector that was invoked
                 * @param event The notification event
                 */
                @Suppress("unused")
                fun callback(self: ID?, selector: Pointer?, event: ID?) {
                    action()
                }
            }.also { nativeCallbackReference = it }
        observeScrollbarTrackBehavior(callback)
        observeScrollbarVisibility(callback)
    }

    private fun <T : Callback> observeScrollbarVisibility(callback: T) {
        callMac {
            val selector = Foundation.createSelector("handleScrollerStyleChanged:") ?: return@callMac
            // Copied from MacScrollBarUI
            Foundation.invoke(
                Foundation.invoke("NSNotificationCenter", "defaultCenter"),
                "addObserver:selector:name:object:",
                Foundation.createDelegate("JewelScrollbarVisibilityObserver", selector, callback),
                Foundation.createSelector("handleScrollerStyleChanged:"),
                Foundation.nsString("NSPreferredScrollerStyleDidChangeNotification"),
                ID.NIL,
            )
        }
    }

    private fun <T : Callback> observeScrollbarTrackBehavior(callback: T) {
        callMac {
            val selector = Foundation.createSelector("handleBehaviorChanged:") ?: return@callMac

            // Copied from MacScrollBarUI
            Foundation.invoke(
                Foundation.invoke("NSDistributedNotificationCenter", "defaultCenter"),
                "addObserver:selector:name:object:",
                Foundation.createDelegate("JewelScrollbarTrackClickBehaviorObserver", selector, callback),
                Foundation.createSelector("handleBehaviorChanged:"),
                Foundation.nsString("AppleNoRedisplayAppearancePreferenceChanged"),
                ID.NIL,
                2, // NSNotificationSuspensionBehaviorCoalesce
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    public fun <T : Any> callMac(producer: () -> T?): T? {
        if (!hostOs.isMacOS) return null

        val pool = Foundation.NSAutoreleasePool()
        try {
            return producer()
        } catch (e: Throwable) {
            logger.log(Level.WARNING, e.toString())
        } finally {
            pool.drain()
        }
        return null
    }
}
