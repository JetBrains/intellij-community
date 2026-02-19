// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.abs
import kotlin.math.tan

private const val ANGLE_EPSILON = .001

/**
 * A brush that draws a linear gradient like CSS would.
 *
 * For an in-depth explanation of how this works, refer to
 * [this article](https://blog.sebastiano.dev/say-hi-like-youre-ai-gradient-text-in-compose-for-desktop/).
 *
 * @param angleDegrees The angle of the gradient in degrees. 0 degrees is a horizontal gradient from left to right, and
 *   90 degrees is a vertical gradient from top to bottom.
 * @param colors The colors of the gradient.
 * @param stops The relative positions of the colors in the gradient, in the range `[0, 1]`. If `null`, the colors will
 *   be evenly distributed.
 * @param scaleX A factor to horizontally scale the gradient brush.
 * @param scaleY A factor to vertically scale the gradient brush.
 * @param offset An offset to apply to the gradient brush.
 */
private class CssLinearGradientBrush(
    private val angleDegrees: Double,
    private var colors: List<Color>,
    private var stops: List<Float>? = null,
    private var scaleX: Float = 1f,
    private var scaleY: Float = 1f,
    private var offset: Offset = Offset.Zero,
) : ShaderBrush() {

    init {
        val colorStops = stops
        check(colorStops == null || colors.size == colorStops.size) { "The number of stops and colors must match" }
        check(colors.isNotEmpty()) { "Specify at least one color and stop" }
    }

    override fun createShader(size: Size): Shader {
        val normalizedAngle = angleDegrees % 360.0
        val adjustedSize = Size(size.width * scaleX, size.height * scaleY)

        // Handle base cases (vertical and horizontal gradient) separately
        return when {
            abs(normalizedAngle % 180.0) < ANGLE_EPSILON -> {
                val leftToRight = abs(normalizedAngle) < 90.0
                createHorizontalGradient(adjustedSize, leftToRight, offset)
            }
            abs(abs(normalizedAngle) - 90.0) < ANGLE_EPSILON -> {
                val startsFromTop = normalizedAngle >= 0.0
                createVerticalGradient(adjustedSize, startsFromTop, offset)
            }
            else -> createLinearGradient(adjustedSize, normalizedAngle, offset)
        }
    }

    private fun createHorizontalGradient(size: Size, leftToRight: Boolean, offset: Offset): Shader {
        val startX = if (leftToRight) 0f else size.width
        val endX = if (leftToRight) size.width else 0f

        val offsetX = size.width * offset.x
        val offsetY = size.height * offset.y

        return LinearGradientShader(
            from = Offset(startX + offsetX, size.height / 2 + offsetY),
            to = Offset(endX + offsetX, size.height / 2 + offsetY),
            colors = colors,
            colorStops = stops,
        )
    }

    private fun createVerticalGradient(size: Size, topToBottom: Boolean, offset: Offset): Shader {
        val startY = if (topToBottom) 0f else size.height
        val endY = if (topToBottom) size.height else 0f

        val offsetX = size.width * offset.x
        val offsetY = size.height * offset.y

        return LinearGradientShader(
            from = Offset(size.width / 2 + offsetX, startY + offsetY),
            to = Offset(size.width / 2 + offsetX, endY + offsetY),
            colors = colors,
            colorStops = stops,
        )
    }

    private fun createLinearGradient(size: Size, angleDegrees: Double, offset: Offset): Shader {
        // Calculate the angle in radians
        val angleRadians = Math.toRadians(angleDegrees)

        // Determine the closest corners to the intersection points
        val normalizedAngle = (angleDegrees + 180) % 360.0
        val (startCorner, endCorner) =
            when {
                normalizedAngle < 90.0 -> Offset(size.width, size.height) to Offset(0f, 0f)
                normalizedAngle < 180.0 -> Offset(0f, size.height) to Offset(size.width, 0f)
                normalizedAngle < 270.0 -> Offset(0f, 0f) to Offset(size.width, size.height)
                else -> Offset(size.width, 0f) to Offset(0f, size.height)
            }

        val offsetX = size.width * offset.x
        val offsetY = size.height * offset.y
        val finalOffset = Offset(offsetX, offsetY)

        val gradientStart = calculateProjection(size.center, angleRadians, startCorner) + finalOffset
        val gradientEnd = calculateProjection(size.center, angleRadians, endCorner) + finalOffset

        // We need to reverse gradient end and start points to get the intended effect
        return LinearGradientShader(from = gradientStart, to = gradientEnd, colors = colors, colorStops = stops)
    }

    private fun calculateProjection(linePoint: Offset, angleRadians: Double, pointToProject: Offset): Offset {
        // Calculate slope from angle
        val m = tan(angleRadians)

        // Calculate y-intercept (b) using the point-slope form
        val b = linePoint.y - m * linePoint.x

        // Equation of the perpendicular line passing through pointToProject
        // m_perp = -1.0 / m
        // y - y1 = m_perp (x - x1)
        // y = m_perp * (x - x1) + y1

        // Solve for intersection point (xp, yp)
        val xp = (b - pointToProject.x / m - pointToProject.y) / (-1.0 / m - m)
        val yp = m * xp + b

        return Offset(xp.toFloat(), yp.toFloat())
    }
}

/**
 * Creates a brush that draws a linear gradient like CSS would.
 *
 * For an in-depth explanation of how this works, refer to
 * [this article](https://blog.sebastiano.dev/say-hi-like-youre-ai-gradient-text-in-compose-for-desktop/).
 *
 * @param angleDegrees The angle of the gradient in degrees. 0 degrees is a horizontal gradient from left to right, and
 *   90 degrees is a vertical gradient from top to bottom.
 * @param colors The colors of the gradient.
 * @param stops The relative positions of the colors in the gradient, in the range `[0, 1]`. If `null`, the colors will
 *   be evenly distributed.
 * @param scaleX A factor to horizontally scale the gradient brush.
 * @param scaleY A factor to vertically scale the gradient brush.
 * @param offset An offset to apply to the gradient brush.
 */
public fun Brush.Companion.cssLinearGradient(
    angleDegrees: Double,
    colors: List<Color>,
    stops: List<Float>? = null,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    offset: Offset = Offset.Zero,
): Brush = CssLinearGradientBrush(angleDegrees, colors, stops, scaleX, scaleY, offset)
