package org.jetbrains.jewel.bridge

import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.jewel.bridge.theme.default
import org.jetbrains.jewel.bridge.theme.macOs
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

internal class ScrollbarHelperTest {
    @Test
    fun `non-Mac systems do not initialize native preferences`() {
        val helper = createScrollbarHelper(false) { error("The native helper must not initialize") }
        assertEquals(ScrollbarVisibility.AlwaysVisible.default(), helper.scrollbarVisibilityStyleFlow.value)
        assertEquals(TrackClickBehavior.JumpToSpot, helper.trackClickBehaviorFlow.value)
    }

    @Test
    fun `Mac systems use the supplied helper`() {
        MacScrollbarHelperImpl(FakePreferences()).use { helper ->
            assertSame(helper, createScrollbarHelper(true) { helper })
        }
    }

    @Test
    fun `initial preferences use Mac styling and paging behavior`() {
        MacScrollbarHelperImpl(FakePreferences()).use { helper ->
            assertEquals(ScrollbarVisibility.AlwaysVisible.macOs(), helper.scrollbarVisibilityStyleFlow.value)
            assertEquals(TrackClickBehavior.NextPage, helper.trackClickBehaviorFlow.value)
        }
        MacScrollbarHelperImpl(FakePreferences(style = 1, jumpToSpot = true)).use { helper ->
            assertEquals(ScrollbarVisibility.WhenScrolling.macOs(), helper.scrollbarVisibilityStyleFlow.value)
            assertEquals(TrackClickBehavior.JumpToSpot, helper.trackClickBehaviorFlow.value)
        }
    }

    @Test
    fun `a notification refreshes both preferences`() {
        val preferences = FakePreferences()
        MacScrollbarHelperImpl(preferences).use { helper ->
            preferences.style = 1
            preferences.jumpToSpot = true
            preferences.notifyChanges()
            assertEquals(ScrollbarVisibility.WhenScrolling.macOs(), helper.scrollbarVisibilityStyleFlow.value)
            assertEquals(TrackClickBehavior.JumpToSpot, helper.trackClickBehaviorFlow.value)

            preferences.style = 0
            preferences.jumpToSpot = false
            preferences.notifyChanges()
            assertEquals(ScrollbarVisibility.AlwaysVisible.macOs(), helper.scrollbarVisibilityStyleFlow.value)
            assertEquals(TrackClickBehavior.NextPage, helper.trackClickBehaviorFlow.value)
        }
    }

    @Test
    fun `failed reads retain previous values and do not block the other preference`() {
        val preferences = FakePreferences()
        MacScrollbarHelperImpl(preferences).use { helper ->
            preferences.style = 1
            preferences.jumpToSpot = true
            preferences.behaviorFailure = IllegalStateException("Cannot read the click behavior")
            preferences.notifyChanges()
            assertEquals(ScrollbarVisibility.WhenScrolling.macOs(), helper.scrollbarVisibilityStyleFlow.value)
            assertEquals(TrackClickBehavior.NextPage, helper.trackClickBehaviorFlow.value)

            preferences.behaviorFailure = null
            preferences.styleFailure = IllegalStateException("Cannot read the scroller style")
            preferences.style = 0
            preferences.notifyChanges()
            assertEquals(ScrollbarVisibility.WhenScrolling.macOs(), helper.scrollbarVisibilityStyleFlow.value)
            assertEquals(TrackClickBehavior.JumpToSpot, helper.trackClickBehaviorFlow.value)
        }
    }

    @Test
    fun `failed initial reads retain fallback values`() {
        val preferences = FakePreferences()
        preferences.behaviorFailure = IllegalStateException("Cannot read the click behavior")
        preferences.styleFailure = IllegalStateException("Cannot read the scroller style")
        MacScrollbarHelperImpl(preferences).use { helper ->
            assertEquals(ScrollbarVisibility.AlwaysVisible.default(), helper.scrollbarVisibilityStyleFlow.value)
            assertEquals(TrackClickBehavior.JumpToSpot, helper.trackClickBehaviorFlow.value)
        }
    }

    @Test
    fun `failed registration does not prevent initial reads`() {
        val preferences = FakePreferences(style = 1, jumpToSpot = true)
        preferences.registrationFailure = IllegalStateException("Cannot register an observer")
        MacScrollbarHelperImpl(preferences).use { helper ->
            assertEquals(ScrollbarVisibility.WhenScrolling.macOs(), helper.scrollbarVisibilityStyleFlow.value)
            assertEquals(TrackClickBehavior.JumpToSpot, helper.trackClickBehaviorFlow.value)
        }
        assertEquals(0, preferences.closeCalls)
    }

    @Test
    fun `closing the helper stops updates and closes the subscription once`() {
        val preferences = FakePreferences()
        val helper = MacScrollbarHelperImpl(preferences)
        helper.close()
        helper.close()
        preferences.style = 1
        preferences.jumpToSpot = true
        preferences.notifyChanges()
        assertEquals(1, preferences.closeCalls)
        assertEquals(ScrollbarVisibility.AlwaysVisible.macOs(), helper.scrollbarVisibilityStyleFlow.value)
        assertEquals(TrackClickBehavior.NextPage, helper.trackClickBehaviorFlow.value)
    }

    @Test
    fun `cancellation propagates and closes a partially initialized helper`() {
        val preferences = FakePreferences()
        val failure = ProcessCanceledException()
        preferences.behaviorFailure = failure
        assertSame(failure, assertThrows(ProcessCanceledException::class.java) { MacScrollbarHelperImpl(preferences) })
        assertEquals(1, preferences.closeCalls)
    }

    private class FakePreferences(var style: Long = 0, var jumpToSpot: Boolean = false) : MacScrollbarPreferences {
        var behaviorFailure: RuntimeException? = null
        var styleFailure: RuntimeException? = null
        var registrationFailure: RuntimeException? = null
        var closeCalls = 0
        private var listener: Runnable? = null

        override fun getPreferredStyle(): Long {
            styleFailure?.let { throw it }
            return style
        }

        override fun isJumpToSpot(): Boolean {
            behaviorFailure?.let { throw it }
            return jumpToSpot
        }

        override fun observeChanges(listener: Runnable): AutoCloseable {
            registrationFailure?.let { throw it }
            this.listener = listener
            return AutoCloseable { closeCalls++ }
        }

        fun notifyChanges() {
            listener?.run()
        }
    }
}
