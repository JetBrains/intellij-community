// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class SliderAdapterTest {
    @Test
    public fun `a drag scrolls when the thumb has room to move`() {
        val targets = dragBy(trackSize = 200)

        assertEquals(1, targets.size)
        assertTrue("Expected a forward target, but it is ${targets.single()}", targets.single() > 0.0)
    }

    @Test
    public fun `a drag does nothing when the thumb fills the track`() {
        assertEquals(emptyList<Double>(), dragBy(trackSize = MIN_THUMB_LENGTH.toInt()))
    }

    @Test
    public fun `a drag does nothing when the thumb is longer than the track`() {
        assertEquals(emptyList<Double>(), dragBy(trackSize = MIN_THUMB_LENGTH.toInt() - 4))
    }

    /** Drags the thumb by 5 pixels, and returns the scroll targets that the adapter received. */
    private fun dragBy(trackSize: Int): List<Double> {
        val adapter = RecordingAdapter()
        val failures = mutableListOf<Throwable>()
        // Unconfined runs the drag inside the call, so the test can read the result right after it.
        @Suppress("InjectDispatcher")
        val scope = CoroutineScope(Dispatchers.Unconfined + CoroutineExceptionHandler { _, error -> failures += error })
        val slider =
            SliderAdapter(
                adapter = adapter,
                trackSize = trackSize,
                minHeight = MIN_THUMB_LENGTH,
                reverseLayout = false,
                isVertical = true,
                coroutineScope = scope,
                scrollState = ScrollableState { it },
            )

        slider.onDragStarted()
        slider.onDragDelta(Offset(0f, 5f))

        assertEquals(emptyList<Throwable>(), failures)
        return adapter.targets
    }

    private class RecordingAdapter : ScrollbarAdapter {
        override val scrollOffset: Double = 0.0
        override val contentSize: Double = 1000.0
        override val viewportSize: Double = 100.0
        val targets = mutableListOf<Double>()

        override suspend fun scrollTo(scrollOffset: Double) {
            targets += scrollOffset
        }
    }

    private companion object {
        const val MIN_THUMB_LENGTH = 24f
    }
}
