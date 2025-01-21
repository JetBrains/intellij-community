package org.jetbrains.jewel.bridge

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.sun.jna.Callback
import com.sun.jna.Pointer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.jewel.bridge.theme.default
import org.jetbrains.jewel.bridge.theme.macOs
import org.jetbrains.jewel.foundation.util.myLogger
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

internal interface ScrollbarHelper {
    val scrollbarVisibilityStyleFlow: StateFlow<ScrollbarVisibility>
    val trackClickBehaviorFlow: StateFlow<TrackClickBehavior>

    companion object {
        @JvmStatic
        fun getInstance(): ScrollbarHelper = if (hostOs == OS.MacOS) scrollbarService else DummyScrollbarHelper
    }
}

private val scrollbarService by lazy { MacScrollbarHelperImpl() }

private class MacScrollbarHelperImpl : Callback, ScrollbarHelper {
    private val logger = myLogger()

    private val _scrollbarVisibilityStyleFlow =
        MutableStateFlow<ScrollbarVisibility>(ScrollbarVisibility.AlwaysVisible.default())
    override val scrollbarVisibilityStyleFlow: StateFlow<ScrollbarVisibility> = _scrollbarVisibilityStyleFlow

    private val _trackClickBehaviorFlow = MutableStateFlow(TrackClickBehavior.JumpToSpot)
    override val trackClickBehaviorFlow: StateFlow<TrackClickBehavior> = _trackClickBehaviorFlow

    init {
        if (hostOs != OS.MacOS) {
            logger.error("${javaClass.simpleName} should only be initialized on macOS.")
        } else {
            callback(null, null, null)

            listenToTrackClickBehaviorChange()
            listenToScrollbarVisibilityChange()
        }
    }

    private fun listenToTrackClickBehaviorChange() {
        callMac {
            // Copied from MacScrollBarUI
            Foundation.invoke(
                Foundation.invoke("NSDistributedNotificationCenter", "defaultCenter"),
                "addObserver:selector:name:object:",
                createDelegate(
                    "JewelScrollbarTrackClickBehaviorObserver",
                    Foundation.createSelector("handleBehaviorChanged:"),
                    this,
                ),
                Foundation.createSelector("handleBehaviorChanged:"),
                Foundation.nsString("AppleNoRedisplayAppearancePreferenceChanged"),
                ID.NIL,
                2, // NSNotificationSuspensionBehaviorCoalesce
            )
        }
    }

    private fun listenToScrollbarVisibilityChange() {
        callMac {
            // Copied from MacScrollBarUI
            Foundation.invoke(
                Foundation.invoke("NSNotificationCenter", "defaultCenter"),
                "addObserver:selector:name:object:",
                createDelegate(
                    "JewelScrollbarVisibilityObserver",
                    Foundation.createSelector("handleScrollerStyleChanged:"),
                    this,
                ),
                Foundation.createSelector("handleScrollerStyleChanged:"),
                Foundation.nsString("NSPreferredScrollerStyleDidChangeNotification"),
                ID.NIL,
            )
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun callback(self: ID?, selector: Pointer?, event: ID?) {
        readTrackClickBehavior()
        readScrollbarVisibility()
    }

    private fun readTrackClickBehavior() {
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

            logger.debug("Scrollbar track click behavior: $behavior")
            _trackClickBehaviorFlow.value = behavior
        }
    }

    private fun readScrollbarVisibility() {
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

            logger.debug("Scrollbar visibility style: $visibility")
            _scrollbarVisibilityStyleFlow.value = visibility
        }
    }

    // Copied from MacScrollBarUI
    @Suppress("detekt:TooGenericExceptionCaught") // Copied from IJP
    private fun <T : Any> callMac(producer: () -> T?): T? {
        if (!SystemInfoRt.isMac) {
            return null
        }

        val pool = Foundation.NSAutoreleasePool()
        try {
            return producer()
        } catch (e: Throwable) {
            logger.warn(e)
        } finally {
            pool.drain()
        }
        return null
    }

    // Copied from MacScrollBarUI
    private fun createDelegate(name: String, pointer: Pointer, callback: Callback): ID {
        val delegateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), name)
        if (ID.NIL != delegateClass) {
            if (!Foundation.addMethod(delegateClass, pointer, callback, "v@")) {
                @Suppress("detekt:TooGenericExceptionThrown") // Copied from IJP
                throw RuntimeException("Cannot add observer method")
            }
            Foundation.registerObjcClassPair(delegateClass)
        }
        return Foundation.invoke(name, "new")
    }
}

private object DummyScrollbarHelper : ScrollbarHelper {
    override val scrollbarVisibilityStyleFlow: StateFlow<ScrollbarVisibility> =
        MutableStateFlow(ScrollbarVisibility.AlwaysVisible.default())
    override val trackClickBehaviorFlow: StateFlow<TrackClickBehavior> = MutableStateFlow(TrackClickBehavior.JumpToSpot)
}
