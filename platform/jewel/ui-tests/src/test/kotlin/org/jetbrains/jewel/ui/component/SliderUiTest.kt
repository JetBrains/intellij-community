// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.jewel.intui.standalone.styling.defaults
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.styling.SliderColors
import org.jetbrains.jewel.ui.component.styling.SliderMetrics
import org.jetbrains.jewel.ui.component.styling.SliderStyle
import org.junit.Rule
import org.junit.Test

class SliderUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `tall thumb centers the track vertically on the thumb`() {
        val thumbSize = DpSize(14.dp, 24.dp)
        setSliderForPixelCheck(value = 0f, thumbSize = thumbSize)

        val pixels = rule.onNodeWithTag(CONTAINER_TAG).captureToImage().toPixelMap()
        val thumbX = ((THUMB_BORDER_WIDTH + thumbSize.width / 2).value * 1f).roundToInt()
        val thumbCenterY = pixels.verticalCenterY(x = thumbX, color = THUMB_FILL_COLOR)
        val trackCenterY = pixels.verticalCenterY(x = pixels.width / 2, color = TRACK_COLOR)

        assertEquals(12.5f, thumbCenterY)
        assertEquals(thumbCenterY, trackCenterY)
    }

    @Test
    fun `wide thumb centers the track vertically on the thumb`() {
        val thumbSize = DpSize(24.dp, 14.dp)
        setSliderForPixelCheck(value = 0f, thumbSize = thumbSize)

        val pixels = rule.onNodeWithTag(CONTAINER_TAG).captureToImage().toPixelMap()
        val thumbX = ((THUMB_BORDER_WIDTH + thumbSize.width / 2).value * 1f).roundToInt()
        val thumbCenterY = pixels.verticalCenterY(x = thumbX, color = THUMB_FILL_COLOR)
        val trackCenterY = pixels.verticalCenterY(x = pixels.width / 2, color = TRACK_COLOR)

        assertEquals(7.5f, thumbCenterY)
        assertEquals(thumbCenterY, trackCenterY)
    }

    @Test
    fun `filled track centers vertically on a non-square thumb`() {
        val thumbSize = DpSize(14.dp, 24.dp)
        setSliderForPixelCheck(value = 1f, thumbSize = thumbSize)

        val pixels = rule.onNodeWithTag(CONTAINER_TAG).captureToImage().toPixelMap()
        val thumbX = pixels.width - ((THUMB_BORDER_WIDTH + thumbSize.width / 2).value * 1f).roundToInt()
        val thumbCenterY = pixels.verticalCenterY(x = thumbX, color = THUMB_FILL_COLOR)
        val filledTrackCenterY = pixels.verticalCenterY(x = pixels.width / 2, color = TRACK_FILLED_COLOR)

        assertEquals(12.5f, thumbCenterY)
        assertEquals(thumbCenterY, filledTrackCenterY)
    }

    @Test
    fun `tall thumb centers the track vertically at 2x density`() {
        val density = 2f
        val thumbSize = DpSize(14.dp, 24.dp)
        setSliderForPixelCheck(value = 0f, thumbSize = thumbSize, density = density)

        val pixels = rule.onNodeWithTag(CONTAINER_TAG).captureToImage().toPixelMap()
        val thumbX = ((THUMB_BORDER_WIDTH + thumbSize.width / 2).value * density).roundToInt()
        val thumbCenterY = pixels.verticalCenterY(x = thumbX, color = THUMB_FILL_COLOR)
        val trackCenterY = pixels.verticalCenterY(x = pixels.width / 2, color = TRACK_COLOR)

        assertEquals(25.5f, thumbCenterY)
        assertEquals(thumbCenterY, trackCenterY)
    }

    @Test
    fun `wide thumb expands slider past container narrower than doubled thumb width`() {
        val style = testSliderStyle(thumbSize = DpSize(60.dp, 14.dp))
        rule.setContent {
            IntUiTheme { Box(Modifier.width(110.dp)) { Slider(value = 0.5f, onValueChange = {}, style = style) } }
        }

        val bounds =
            rule
                .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                .getUnclippedBoundsInRoot()
        assertEquals(120.dp, bounds.width)
        assertEquals(16.dp, bounds.height)
    }

    @Test
    fun `tall thumb does not expand slider past container wider than doubled thumb width`() {
        val style = testSliderStyle(thumbSize = DpSize(14.dp, 60.dp))
        rule.setContent {
            IntUiTheme { Box(Modifier.width(110.dp)) { Slider(value = 0.5f, onValueChange = {}, style = style) } }
        }

        val bounds =
            rule
                .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                .getUnclippedBoundsInRoot()
        assertEquals(110.dp, bounds.width)
        assertEquals(62.dp, bounds.height)
    }

    @Test
    fun `narrow container enforces doubled tall thumb width as minimum width`() {
        val style = testSliderStyle(thumbSize = DpSize(14.dp, 24.dp))
        rule.setContent {
            IntUiTheme { Box(Modifier.width(10.dp)) { Slider(value = 0.5f, onValueChange = {}, style = style) } }
        }

        val width =
            rule
                .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                .getUnclippedBoundsInRoot()
                .width
        assertEquals(28.dp, width)
    }

    @Test
    fun `narrow container enforces doubled wide thumb width as minimum width`() {
        val style = testSliderStyle(thumbSize = DpSize(24.dp, 14.dp))
        rule.setContent {
            IntUiTheme { Box(Modifier.width(10.dp)) { Slider(value = 0.5f, onValueChange = {}, style = style) } }
        }

        val width =
            rule
                .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                .getUnclippedBoundsInRoot()
                .width
        assertEquals(48.dp, width)
    }

    private fun setSliderForPixelCheck(value: Float, thumbSize: DpSize, density: Float = 1f) {
        val style = testSliderStyle(thumbSize = thumbSize)
        rule.setContent {
            IntUiTheme {
                CompositionLocalProvider(LocalDensity provides Density(density)) {
                    PixelCheckContainer {
                        Slider(value = value, onValueChange = {}, modifier = Modifier.width(200.dp), style = style)
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun PixelCheckContainer(content: @Composable () -> Unit) {
        Box(Modifier.background(BACKGROUND_COLOR).testTag(CONTAINER_TAG)) { content() }
    }

    private fun PixelMap.verticalCenterY(x: Int, color: Color): Float {
        val matchingRows = (0..<height).filter { y -> this[x, y] == color }
        assertTrue(matchingRows.isNotEmpty(), "Expected pixels of $color at x=$x, but found none")
        return (matchingRows.first() + matchingRows.last()) / 2f
    }

    private fun testSliderStyle(thumbSize: DpSize): SliderStyle =
        SliderStyle(
            colors =
                SliderColors(
                    track = TRACK_COLOR,
                    trackFilled = TRACK_FILLED_COLOR,
                    trackDisabled = TRACK_COLOR,
                    trackFilledDisabled = TRACK_FILLED_COLOR,
                    stepMarker = TRACK_COLOR,
                    thumbFill = THUMB_FILL_COLOR,
                    thumbFillDisabled = THUMB_FILL_COLOR,
                    thumbFillFocused = THUMB_FILL_COLOR,
                    thumbFillPressed = THUMB_FILL_COLOR,
                    thumbFillHovered = THUMB_FILL_COLOR,
                    thumbBorder = THUMB_BORDER_COLOR,
                    thumbBorderFocused = THUMB_BORDER_COLOR,
                    thumbBorderDisabled = THUMB_BORDER_COLOR,
                    thumbBorderPressed = THUMB_BORDER_COLOR,
                    thumbBorderHovered = THUMB_BORDER_COLOR,
                ),
            metrics =
                SliderMetrics.defaults(
                    trackHeight = TRACK_HEIGHT,
                    thumbSize = thumbSize,
                    thumbBorderWidth = THUMB_BORDER_WIDTH,
                ),
            thumbShape = CircleShape,
        )

    private companion object {
        const val CONTAINER_TAG = "slider-container"

        val TRACK_HEIGHT = 4.dp
        val THUMB_BORDER_WIDTH = 1.dp

        val BACKGROUND_COLOR = Color(0xFFFFFFFF)
        val TRACK_COLOR = Color(0xFFFF0000)
        val TRACK_FILLED_COLOR = Color(0xFF00AA00)
        val THUMB_FILL_COLOR = Color(0xFF0000FF)
        val THUMB_BORDER_COLOR = Color(0xFF000000)
    }
}
