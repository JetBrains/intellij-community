package org.jetbrains.jewel.bridge

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.Foundation.NSAutoreleasePool
import com.intellij.ui.mac.foundation.ID
import com.sun.jna.Callback
import com.sun.jna.Pointer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.jewel.bridge.theme.defaults
import org.jetbrains.jewel.foundation.util.myLogger
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior

internal object MacScrollbarHelper {
    private val _scrollbarVisibilityStyleFlow = MutableStateFlow(scrollbarVisibility)
    val scrollbarVisibilityStyleFlow: StateFlow<ScrollbarVisibility> = _scrollbarVisibilityStyleFlow

    private val _trackClickBehaviorFlow = MutableStateFlow(trackClickBehavior)
    val trackClickBehaviorFlow: StateFlow<TrackClickBehavior> = _trackClickBehaviorFlow

    init {
        if (SystemInfoRt.isMac) {
            initNotificationObserver()
        }
    }

    val trackClickBehavior: TrackClickBehavior
        get() {
            if (!SystemInfoRt.isMac) {
                return TrackClickBehavior.JumpToSpot
            }

            val pool = NSAutoreleasePool()
            try {
                return readMacScrollbarBehavior()
            } finally {
                pool.drain()
            }
        }

    val scrollbarVisibility: ScrollbarVisibility
        get() {
            if (!SystemInfoRt.isMac) {
                return ScrollbarVisibility.AlwaysVisible
            }

            val pool = NSAutoreleasePool()
            try {
                return readMacScrollbarStyle()
            } catch (ignore: Throwable) {
            } finally {
                pool.drain()
            }
            return ScrollbarVisibility.AlwaysVisible
        }

    private fun initNotificationObserver() {
        val pool = NSAutoreleasePool()

        val delegateClass =
            Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), "NSScrollerChangesObserver")
        if (ID.NIL != delegateClass) {
            if (!addScrollbarVisibilityChangeListener(delegateClass)) {
                myLogger().error("Cannot add observer method")
            }

            if (!addTrackClickBehaviorChangeListener(delegateClass)) {
                myLogger().error("Cannot add observer method")
            }
            Foundation.registerObjcClassPair(delegateClass)
        }
        val delegate = Foundation.invoke("NSScrollerChangesObserver", "new")

        try {
            var center = Foundation.invoke("NSNotificationCenter", "defaultCenter")
            Foundation.invoke(
                center,
                "addObserver:selector:name:object:",
                delegate,
                Foundation.createSelector("handleScrollerStyleChanged:"),
                Foundation.nsString("NSPreferredScrollerStyleDidChangeNotification"),
                ID.NIL,
            )

            center = Foundation.invoke("NSDistributedNotificationCenter", "defaultCenter")
            Foundation.invoke(
                center,
                "addObserver:selector:name:object:",
                delegate,
                Foundation.createSelector("handleBehaviorChanged:"),
                Foundation.nsString("AppleNoRedisplayAppearancePreferenceChanged"),
                ID.NIL,
                2, // NSNotificationSuspensionBehaviorCoalesce
            )
        } finally {
            pool.drain()
        }
    }

    private val APPEARANCE_CALLBACK: Callback =
        object : Callback {
            @Suppress("UNUSED_PARAMETER", "unused")
            @SuppressWarnings("UnusedDeclaration")
            fun callback(
                self: ID?,
                selector: Pointer?,
                event: ID?,
            ) {
                _scrollbarVisibilityStyleFlow.tryEmit(scrollbarVisibility)
            }
        }

    private val BEHAVIOR_CALLBACK: Callback =
        object : Callback {
            @Suppress("UNUSED_PARAMETER", "unused")
            @SuppressWarnings("UnusedDeclaration")
            fun callback(
                self: ID?,
                selector: Pointer?,
                event: ID?,
            ) {
                _trackClickBehaviorFlow.tryEmit(trackClickBehavior)
            }
        }

    private fun readMacScrollbarBehavior(): TrackClickBehavior {
        val defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults")
        Foundation.invoke(defaults, "synchronize")
        return Foundation
            .invoke(defaults, "boolForKey:", Foundation.nsString("AppleScrollerPagingBehavior"))
            .run { if (toInt() == 1) TrackClickBehavior.JumpToSpot else TrackClickBehavior.NextPage }
    }

    private fun readMacScrollbarStyle(): ScrollbarVisibility {
        val nsScroller = Foundation.invoke(Foundation.getObjcClass("NSScroller"), "preferredScrollerStyle")

        val visibility: ScrollbarVisibility =
            if (1 == nsScroller.toInt()) {
                ScrollbarVisibility.WhenScrolling.Companion.defaults()
            } else {
                ScrollbarVisibility.AlwaysVisible
            }
        return visibility
    }

    private fun addScrollbarVisibilityChangeListener(delegateClass: ID?) =
        Foundation.addMethod(
            delegateClass,
            Foundation.createSelector("handleScrollerStyleChanged:"),
            APPEARANCE_CALLBACK,
            "v@",
        )

    private fun addTrackClickBehaviorChangeListener(delegateClass: ID?) =
        Foundation.addMethod(
            delegateClass,
            Foundation.createSelector("handleBehaviorChanged:"),
            BEHAVIOR_CALLBACK,
            "v@",
        )
}
