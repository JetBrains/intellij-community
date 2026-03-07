// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import com.sun.jna.Callback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.intui.standalone.window.macos.MacPlatformServices
import org.jetbrains.jewel.intui.standalone.window.macos.MacPlatformServicesDefaultImpl
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

/**
 * Provides access to platform-specific scrollbar settings and observes changes to those settings.
 *
 * This interface abstracts scrollbar behavior and visibility preferences that are configured at the operating system
 * level. On macOS, it reads and observes NSScroller preferences. On other platforms, it provides default fallback
 * values.
 *
 * The companion object automatically delegates to the appropriate implementation based on the current platform.
 */
@ApiStatus.Internal
@InternalJewelApi
public interface ScrollbarHelper {
    /** A [StateFlow] that emits the current scrollbar visibility style whenever it changes. */
    public val scrollbarVisibilityStyleFlow: StateFlow<ScrollbarVisibility>

    /** A [StateFlow] that emits the current track click behavior whenever it changes. */
    public val trackClickBehaviorFlow: StateFlow<TrackClickBehavior>

    /**
     * The current track click behavior, read directly from system preferences. This property reads the value on each
     * access.
     */
    public val trackClickBehavior: TrackClickBehavior

    /**
     * The current scrollbar visibility style, read directly from system preferences. This property reads the value on
     * each access.
     */
    public val scrollbarVisibilityStyle: ScrollbarVisibility

    public companion object : ScrollbarHelper by if (hostOs == OS.MacOS) scrollbarService else DummyScrollbarHelper
}

private val scrollbarService by lazy { StandaloneScrollbarHelper() }

/**
 * macOS-specific implementation of [ScrollbarHelper] that reads scrollbar preferences from the system and observes
 * changes via NSNotificationCenter.
 *
 * This class implements [Callback] to receive notifications from macOS when scrollbar-related system preferences
 * change, including:
 * - Scrollbar visibility style (always visible vs. overlay/when scrolling)
 * - Track click behavior (jump to spot vs. next page)
 *
 * When a notification is received, the [callback] method is invoked, which updates the internal state flows that
 * consumers can observe reactively.
 */
@ApiStatus.Internal
@InternalJewelApi
public class StandaloneScrollbarHelper(
    private val macPlatformServices: MacPlatformServices = MacPlatformServicesDefaultImpl
) : ScrollbarHelper {
    private val _trackClickBehaviorFlow = MutableStateFlow(macPlatformServices.readScrollbarTrackClickBehavior())
    override val trackClickBehaviorFlow: StateFlow<TrackClickBehavior> = _trackClickBehaviorFlow

    private val _scrollbarVisibilityStyleFlow = MutableStateFlow(macPlatformServices.readScrollbarVisibility())
    override val scrollbarVisibilityStyleFlow: StateFlow<ScrollbarVisibility> = _scrollbarVisibilityStyleFlow

    override val trackClickBehavior: TrackClickBehavior
        get() = macPlatformServices.readScrollbarTrackClickBehavior()

    override val scrollbarVisibilityStyle: ScrollbarVisibility
        get() = macPlatformServices.readScrollbarVisibility()

    init {
        macPlatformServices.onPreferencesChanged {
            _trackClickBehaviorFlow.value = macPlatformServices.readScrollbarTrackClickBehavior()
            _scrollbarVisibilityStyleFlow.value = macPlatformServices.readScrollbarVisibility()
        }
    }
}

private object DummyScrollbarHelper : ScrollbarHelper {
    override val scrollbarVisibilityStyleFlow: StateFlow<ScrollbarVisibility> =
        MutableStateFlow(ScrollbarVisibility.AlwaysVisible.default())
    override val trackClickBehaviorFlow: StateFlow<TrackClickBehavior> = MutableStateFlow(TrackClickBehavior.JumpToSpot)

    override val trackClickBehavior: TrackClickBehavior
        get() = TrackClickBehavior.JumpToSpot

    override val scrollbarVisibilityStyle: ScrollbarVisibility
        get() = ScrollbarVisibility.AlwaysVisible.default()
}
