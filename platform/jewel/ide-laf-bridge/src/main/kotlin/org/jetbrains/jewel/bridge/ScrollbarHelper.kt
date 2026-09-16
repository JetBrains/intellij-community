package org.jetbrains.jewel.bridge

import com.intellij.openapi.diagnostic.getOrHandleException
import java.util.concurrent.atomic.AtomicBoolean
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
        @JvmStatic fun getInstance(): ScrollbarHelper = createScrollbarHelper(hostOs == OS.MacOS) { scrollbarService }
    }
}

private val scrollbarService by lazy { MacScrollbarHelperImpl() }

internal fun createScrollbarHelper(isMac: Boolean, macHelper: () -> ScrollbarHelper): ScrollbarHelper =
    if (isMac) macHelper() else DummyScrollbarHelper

internal class MacScrollbarHelperImpl(private val preferences: MacScrollbarPreferences = FfmMacScrollbarPreferences) :
    ScrollbarHelper, AutoCloseable {
    private val logger = myLogger()

    private val _scrollbarVisibilityStyleFlow =
        MutableStateFlow<ScrollbarVisibility>(ScrollbarVisibility.AlwaysVisible.default())
    override val scrollbarVisibilityStyleFlow: StateFlow<ScrollbarVisibility> = _scrollbarVisibilityStyleFlow

    private val _trackClickBehaviorFlow = MutableStateFlow(TrackClickBehavior.JumpToSpot)
    override val trackClickBehaviorFlow: StateFlow<TrackClickBehavior> = _trackClickBehaviorFlow

    private val closed = AtomicBoolean()
    private val subscription = callMac { preferences.observeChanges(::refresh) }

    init {
        var initialized = false
        try {
            refresh()
            initialized = true
        } finally {
            if (!initialized) close()
        }
    }

    private fun refresh() {
        if (closed.get()) return
        readTrackClickBehavior()
        readScrollbarVisibility()
    }

    private fun readTrackClickBehavior() {
        callMac {
            val behavior =
                if (preferences.isJumpToSpot()) {
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
            val visibility =
                if (preferences.getPreferredStyle() != 0L) {
                    ScrollbarVisibility.WhenScrolling.macOs()
                } else {
                    ScrollbarVisibility.AlwaysVisible.macOs()
                }

            logger.debug("Scrollbar visibility style: $visibility")
            _scrollbarVisibilityStyleFlow.value = visibility
        }
    }

    private fun <T : Any> callMac(producer: () -> T?): T? =
        runCatching(producer).getOrHandleException { logger.warn(it) }

    override fun close() {
        if (closed.compareAndSet(false, true)) subscription?.close()
    }
}

private object DummyScrollbarHelper : ScrollbarHelper {
    override val scrollbarVisibilityStyleFlow: StateFlow<ScrollbarVisibility> =
        MutableStateFlow(ScrollbarVisibility.AlwaysVisible.default())
    override val trackClickBehaviorFlow: StateFlow<TrackClickBehavior> = MutableStateFlow(TrackClickBehavior.JumpToSpot)
}
