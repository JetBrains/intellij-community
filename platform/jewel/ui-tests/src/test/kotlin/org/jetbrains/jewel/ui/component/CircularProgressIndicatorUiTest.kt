// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle
import org.junit.Rule
import org.junit.Test

class CircularProgressIndicatorUiTest {
    @get:Rule val rule = createComposeRule()

    private val frameTime = 125.milliseconds

    @Test
    fun `should only redraw once per animation frame`() {
        val durationMillis = 2_000L
        val drawPasses = countDrawPasses(frameTime, durationMillis)

        // One draw per animation frame, plus at most one for rounding at each end.
        val expected = (durationMillis / frameTime.inWholeMilliseconds).toInt()
        assertTrue(
            drawPasses <= expected + 1,
            "Expected at most ${expected + 1} draw passes in ${durationMillis}ms, but got $drawPasses. " +
                "The spinner is likely redrawing on every display frame instead of once per animation frame.",
        )
        assertTrue(drawPasses > 0, "Expected the spinner to animate, but it never redrew")
    }

    @Test
    fun `should redraw more often with a shorter frame time`() {
        val slowPasses = countDrawPasses(200.milliseconds, 2_000)
        rule.mainClock.autoAdvance = true

        assertTrue(slowPasses in 1..12, "Expected around 10 draw passes for a 200ms frame time, but got $slowPasses")
    }

    @Test
    fun `should not animate when frame time is not positive`() {
        val drawPasses = countDrawPasses(Duration.ZERO, 2_000)

        assertEquals(0, drawPasses, "A non-positive frame time should render a static spinner, not spin or busy-loop")
    }

    private fun style(frameTime: Duration = this.frameTime) =
        CircularProgressStyle(frameTime = frameTime, color = Color(0xFF6F737A))

    /**
     * Counts draw passes while the animation runs on the virtual clock. The spinner must only redraw when its snapped
     * frame index changes, not on every display frame; see JEWEL-1389.
     */
    private fun countDrawPasses(frameTime: Duration, durationMillis: Long): Int {
        var drawPasses = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                CircularProgressIndicator(
                    modifier =
                        Modifier.drawWithContent {
                            drawPasses++
                            drawContent()
                        },
                    style = style(frameTime),
                )
            }
        }
        rule.waitForIdle()
        drawPasses = 0 // Discard the initial layout and draw pass

        repeat((durationMillis / MILLIS_PER_DISPLAY_FRAME).toInt()) { rule.mainClock.advanceTimeByFrame() }
        return drawPasses
    }

    private companion object {
        private const val MILLIS_PER_DISPLAY_FRAME = 16
    }
}
