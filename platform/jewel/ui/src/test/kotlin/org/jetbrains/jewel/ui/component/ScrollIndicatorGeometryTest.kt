// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import org.jetbrains.jewel.ui.component.styling.ScrollbarColors
import org.jetbrains.jewel.ui.component.styling.ScrollbarMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class ScrollIndicatorGeometryTest {
    @Test
    public fun `reserves a lane only for always-visible scrollbars on macOS`() {
        val style = scrollbarStyle(alwaysVisible(trackThicknessExpanded = 14.dp))

        assertEquals(14.dp, scrollIndicatorReservedThickness(style, isMacOs = true))
        // Windows and Linux always overlay, so there is nothing to reserve there.
        assertEquals(0.dp, scrollIndicatorReservedThickness(style, isMacOs = false))
    }

    @Test
    public fun `reserves nothing for scrollbars that only show while scrolling`() {
        val style = scrollbarStyle(whenScrolling())

        assertEquals(0.dp, scrollIndicatorReservedThickness(style, isMacOs = true))
        assertEquals(0.dp, scrollIndicatorReservedThickness(style, isMacOs = false))
    }

    @Test
    public fun `maps scroll position to a proportional thumb`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 1_000, viewportSizePx = 200, scrollOffsetPx = 200)

        assertEquals(40, geometry.thumbSizePx(trackLengthPx = 200, minThumbLengthPx = 16))
        assertEquals(40, geometry.thumbOffsetPx(trackLengthPx = 200, minThumbLengthPx = 16))
    }

    @Test
    public fun `places the thumb at the track start when not scrolled`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 1_000, viewportSizePx = 200, scrollOffsetPx = 0)

        assertEquals(0, geometry.thumbOffsetPx(trackLengthPx = 200, minThumbLengthPx = 16))
    }

    @Test
    public fun `places the thumb at the track end when fully scrolled`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 1_000, viewportSizePx = 200, scrollOffsetPx = 800)

        assertEquals(160, geometry.thumbOffsetPx(trackLengthPx = 200, minThumbLengthPx = 16))
    }

    @Test
    public fun `clamps a scroll offset beyond the scrollable range`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 1_000, viewportSizePx = 200, scrollOffsetPx = 5_000)

        assertEquals(160, geometry.thumbOffsetPx(trackLengthPx = 200, minThumbLengthPx = 16))
    }

    @Test
    public fun `clamps a negative scroll offset`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 1_000, viewportSizePx = 200, scrollOffsetPx = -500)

        assertEquals(0, geometry.thumbOffsetPx(trackLengthPx = 200, minThumbLengthPx = 16))
    }

    @Test
    public fun `enforces the minimum thumb length for very long content`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 100_000, viewportSizePx = 200, scrollOffsetPx = 0)

        assertEquals(16, geometry.thumbSizePx(trackLengthPx = 200, minThumbLengthPx = 16))
    }

    @Test
    public fun `never grows the thumb past the track length`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 1_000, viewportSizePx = 200, scrollOffsetPx = 0)

        assertEquals(20, geometry.thumbSizePx(trackLengthPx = 20, minThumbLengthPx = 500))
    }

    @Test
    public fun `fills the track when the content fits in the viewport`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 200, viewportSizePx = 200, scrollOffsetPx = 0)

        assertEquals(200, geometry.thumbSizePx(trackLengthPx = 200, minThumbLengthPx = 16))
        assertEquals(0, geometry.thumbOffsetPx(trackLengthPx = 200, minThumbLengthPx = 16))
    }

    @Test
    public fun `reports no thumb when the track has no length`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 1_000, viewportSizePx = 200, scrollOffsetPx = 200)

        assertEquals(0, geometry.thumbSizePx(trackLengthPx = 0, minThumbLengthPx = 16))
        assertEquals(0, geometry.thumbOffsetPx(trackLengthPx = 0, minThumbLengthPx = 16))
    }

    @Test
    public fun `fills the track when the metrics are the unknown sentinel`() {
        val unknown = Int.MAX_VALUE
        val geometry = ScrollIndicatorGeometry(contentSizePx = unknown, viewportSizePx = unknown, scrollOffsetPx = 0)

        assertEquals(200, geometry.thumbSizePx(trackLengthPx = 200, minThumbLengthPx = 16))
        assertEquals(0, geometry.thumbOffsetPx(trackLengthPx = 200, minThumbLengthPx = 16))
    }

    @Test
    public fun `reports no thumb when the viewport has not been measured`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 1_000, viewportSizePx = 0, scrollOffsetPx = 0)

        assertEquals(0, geometry.thumbSizePx(trackLengthPx = 200, minThumbLengthPx = 16))
        assertEquals(0, geometry.thumbOffsetPx(trackLengthPx = 200, minThumbLengthPx = 16))
    }

    private fun scrollbarStyle(visibility: ScrollbarVisibility): ScrollbarStyle =
        ScrollbarStyle(
            colors =
                ScrollbarColors(
                    Color.Unspecified,
                    Color.Unspecified,
                    Color.Unspecified,
                    Color.Unspecified,
                    Color.Unspecified,
                    Color.Unspecified,
                    Color.Unspecified,
                    Color.Unspecified,
                    Color.Unspecified,
                    Color.Unspecified,
                    Color.Unspecified,
                    Color.Unspecified,
                ),
            metrics = ScrollbarMetrics(thumbCornerSize = CornerSize(0.dp), minThumbLength = 16.dp),
            trackClickBehavior = TrackClickBehavior.JumpToSpot,
            scrollbarVisibility = visibility,
        )

    private fun alwaysVisible(trackThicknessExpanded: Dp): ScrollbarVisibility =
        ScrollbarVisibility.AlwaysVisible(
            trackThickness = trackThicknessExpanded,
            trackPadding = PaddingValues(),
            trackPaddingWithBorder = PaddingValues(),
            thumbColorAnimationDuration = Duration.ZERO,
            trackColorAnimationDuration = Duration.ZERO,
            scrollbarBackgroundColorLight = Color.Unspecified,
            scrollbarBackgroundColorDark = Color.Unspecified,
            trackThicknessExpanded = trackThicknessExpanded,
        )

    private fun whenScrolling(): ScrollbarVisibility =
        ScrollbarVisibility.WhenScrolling(
            trackThickness = 8.dp,
            trackThicknessExpanded = 14.dp,
            trackPadding = PaddingValues(),
            trackPaddingWithBorder = PaddingValues(),
            trackColorAnimationDuration = Duration.ZERO,
            expandAnimationDuration = Duration.ZERO,
            thumbColorAnimationDuration = Duration.ZERO,
            lingerDuration = Duration.ZERO,
        )

    @Test
    public fun `thumb start targets clamp to the travel range`() {
        // Track 200, thumb 40 -> travel 160, scrollable 800.
        assertEquals(
            0f,
            targetScrollOffsetForThumbStart(-50f, trackLengthPx = 200, thumbSizePx = 40, maxScrollOffsetPx = 800),
        )
        assertEquals(
            0f,
            targetScrollOffsetForThumbStart(0f, trackLengthPx = 200, thumbSizePx = 40, maxScrollOffsetPx = 800),
        )
        assertEquals(
            400f,
            targetScrollOffsetForThumbStart(80f, trackLengthPx = 200, thumbSizePx = 40, maxScrollOffsetPx = 800),
        )
        assertEquals(
            800f,
            targetScrollOffsetForThumbStart(160f, trackLengthPx = 200, thumbSizePx = 40, maxScrollOffsetPx = 800),
        )
        assertEquals(
            800f,
            targetScrollOffsetForThumbStart(999f, trackLengthPx = 200, thumbSizePx = 40, maxScrollOffsetPx = 800),
        )
    }

    @Test
    public fun `thumb targets are zero without travel or scroll range`() {
        assertEquals(
            0f,
            targetScrollOffsetForThumbStart(80f, trackLengthPx = 40, thumbSizePx = 40, maxScrollOffsetPx = 800),
        )
        assertEquals(
            0f,
            targetScrollOffsetForThumbStart(80f, trackLengthPx = 200, thumbSizePx = 40, maxScrollOffsetPx = 0),
        )
    }

    @Test
    public fun `centre targets offset by half the thumb`() {
        assertEquals(
            400f,
            targetScrollOffsetForThumbCenter(100f, trackLengthPx = 200, thumbSizePx = 40, maxScrollOffsetPx = 800),
        )
    }

    @Test
    public fun `page direction splits before, on, and after the thumb`() {
        assertEquals(-1, pageDirectionTowardPointer(pointerOffsetPx = 9f, thumbOffsetPx = 10, thumbSizePx = 40))
        assertEquals(0, pageDirectionTowardPointer(pointerOffsetPx = 10f, thumbOffsetPx = 10, thumbSizePx = 40))
        assertEquals(0, pageDirectionTowardPointer(pointerOffsetPx = 50f, thumbOffsetPx = 10, thumbSizePx = 40))
        assertEquals(0, pageDirectionTowardPointer(pointerOffsetPx = 50f, thumbOffsetPx = 10, thumbSizePx = 40))
        assertEquals(1, pageDirectionTowardPointer(pointerOffsetPx = 50.5f, thumbOffsetPx = 10, thumbSizePx = 40))
        assertEquals(1, pageDirectionTowardPointer(pointerOffsetPx = 99f, thumbOffsetPx = 10, thumbSizePx = 40))
    }

    @Test
    public fun `the paging latch stops only on a crossed press`() {
        assertEquals(false, pagesBackPastPress(pagingDirection = 0, direction = -1))
        assertEquals(false, pagesBackPastPress(pagingDirection = 1, direction = 1))
        assertEquals(false, pagesBackPastPress(pagingDirection = -1, direction = -1))
        assertEquals(true, pagesBackPastPress(pagingDirection = 1, direction = -1))
        assertEquals(true, pagesBackPastPress(pagingDirection = -1, direction = 1))
    }

    @Test
    public fun `the reserved lane covers only its own strip`() {
        assertFalse(
            isInReservedLane(
                crossAxisPositionPx = 2f,
                crossAxisSizePx = 200,
                laneThicknessPx = 14f,
                laneAtStart = false,
            )
        )
        assertTrue(
            isInReservedLane(
                crossAxisPositionPx = 195f,
                crossAxisSizePx = 200,
                laneThicknessPx = 14f,
                laneAtStart = false,
            )
        )
        assertFalse(
            isInReservedLane(
                crossAxisPositionPx = 180f,
                crossAxisSizePx = 200,
                laneThicknessPx = 14f,
                laneAtStart = false,
            )
        )
        assertTrue(
            isInReservedLane(crossAxisPositionPx = 2f, crossAxisSizePx = 200, laneThicknessPx = 14f, laneAtStart = true)
        )
        assertFalse(
            isInReservedLane(
                crossAxisPositionPx = 20f,
                crossAxisSizePx = 200,
                laneThicknessPx = 14f,
                laneAtStart = true,
            )
        )
    }

    @Test
    public fun `an oversized lane stops at the cross axis start`() {
        // Without the clamp the range would be -100..200 and swallow the whole cross axis.
        assertFalse(
            isInReservedLane(
                crossAxisPositionPx = 250f,
                crossAxisSizePx = 200,
                laneThicknessPx = 300f,
                laneAtStart = false,
            )
        )
        // With the clamp the range is 0..200, so the start of the axis is inside an oversized lane.
        assertTrue(
            isInReservedLane(
                crossAxisPositionPx = 0f,
                crossAxisSizePx = 200,
                laneThicknessPx = 300f,
                laneAtStart = false,
            )
        )
    }

    @Test
    public fun `thumb geometry clamps oversized insets`() {
        val geometry = ScrollIndicatorGeometry(contentSizePx = 1_000, viewportSizePx = 200, scrollOffsetPx = 100)
        val thumb =
            geometry.thumbGeometry(trackLengthPx = 100, startInsetPx = 70, endInsetPx = 70, minThumbLengthPx = 16)
        assertEquals(70, thumb.trackStartPx)
        assertEquals(0, thumb.trackLengthPx)
    }
}
