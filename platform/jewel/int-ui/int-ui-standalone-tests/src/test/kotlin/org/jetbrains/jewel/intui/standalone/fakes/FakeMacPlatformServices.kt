// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.fakes

import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.intui.standalone.window.macos.MacPlatformServices
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior

public open class FakeMacPlatformServices : MacPlatformServices {
    private var listener: (() -> Unit)? = null
    private var currentBehavior: TrackClickBehavior = TrackClickBehavior.JumpToSpot
    private var currentVisibility: ScrollbarVisibility = ScrollbarVisibility.AlwaysVisible.default()
    public var hideCursorCalls: Int = 0
        private set

    override fun hideCursorUntilMoved() {
        hideCursorCalls++
    }

    override fun readScrollbarTrackClickBehavior(): TrackClickBehavior = currentBehavior

    override fun readScrollbarVisibility(): ScrollbarVisibility = currentVisibility

    override fun onPreferencesChanged(action: () -> Unit) {
        listener = action
    }

    public fun simulateSystemChange(
        newBehavior: TrackClickBehavior = currentBehavior,
        newVisibility: ScrollbarVisibility = currentVisibility,
    ) {
        currentBehavior = newBehavior
        currentVisibility = newVisibility

        listener?.invoke()
    }
}
