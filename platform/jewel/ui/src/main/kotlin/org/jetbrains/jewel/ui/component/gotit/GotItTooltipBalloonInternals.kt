// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi

/**
 * Calculates the position of a balloon popup such that its arrow aligns with the given [anchor] point on the target.
 *
 * The popup is placed so that [arrowOffsetPx] pixels from the popup's leading edge land exactly on the resolved anchor
 * point.
 */
@InternalJewelApi
@ApiStatus.Internal
public fun calculateBalloonPosition(
    gotItBalloonPosition: GotItBalloonPosition,
    anchor: Alignment,
    arrowOffsetPx: Int,
    anchorBounds: IntRect,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
): IntOffset {
    val anchorPoint = anchor.align(IntSize.Zero, IntSize(anchorBounds.width, anchorBounds.height), layoutDirection)
    val anchorX = anchorBounds.left + anchorPoint.x
    val anchorY = anchorBounds.top + anchorPoint.y

    return when (gotItBalloonPosition) {
        GotItBalloonPosition.BELOW -> IntOffset(x = anchorX - arrowOffsetPx, y = anchorY)

        GotItBalloonPosition.ABOVE -> IntOffset(x = anchorX - arrowOffsetPx, y = anchorY - popupContentSize.height)

        GotItBalloonPosition.START -> IntOffset(x = anchorX - popupContentSize.width, y = anchorY - arrowOffsetPx)

        GotItBalloonPosition.END -> IntOffset(x = anchorX, y = anchorY - arrowOffsetPx)
    }
}

/**
 * Creates the [Outline] for a balloon shape with a directional arrow.
 *
 * The arrow is drawn on the side facing the anchor — the opposite side from [arrowPosition]:
 * - [GotItBalloonPosition.BELOW] → arrow at the top of the balloon (pointing up)
 * - [GotItBalloonPosition.ABOVE] → arrow at the bottom of the balloon (pointing down)
 * - [GotItBalloonPosition.START] → arrow at the end of the balloon (pointing right/end)
 * - [GotItBalloonPosition.END] → arrow at the start of the balloon (pointing left/start)
 */
@InternalJewelApi
@ApiStatus.Internal
public fun createBalloonOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
    arrowWidth: Dp,
    arrowHeight: Dp,
    cornerRadius: Dp,
    arrowPosition: GotItBalloonPosition,
    arrowOffset: Dp,
): Outline {
    val arrowWidthPx = with(density) { arrowWidth.toPx() }
    val arrowHeightPx = with(density) { arrowHeight.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val arrowOffsetPx = with(density) { arrowOffset.toPx() }

    val isRtl = layoutDirection == LayoutDirection.Rtl

    val rectLeft = if (arrowPosition == GotItBalloonPosition.END) arrowHeightPx else 0f
    val rectTop = if (arrowPosition == GotItBalloonPosition.BELOW) arrowHeightPx else 0f
    val rectRight = size.width - if (arrowPosition == GotItBalloonPosition.START) arrowHeightPx else 0f
    val rectBottom = size.height - if (arrowPosition == GotItBalloonPosition.ABOVE) arrowHeightPx else 0f

    val roundRect = RoundRect(rectLeft, rectTop, rectRight, rectBottom, CornerRadius(cornerRadiusPx))
    val baseRectPath = Path().apply { addRoundRect(roundRect) }
    val arrowPath = Path()

    when (arrowPosition) {
        GotItBalloonPosition.ABOVE,
        GotItBalloonPosition.BELOW -> {
            val actualOffsetPx = if (isRtl) size.width - arrowOffsetPx else arrowOffsetPx

            val center =
                actualOffsetPx.coerceIn(
                    rectLeft + cornerRadiusPx + arrowWidthPx / 2,
                    rectRight - cornerRadiusPx - arrowWidthPx / 2,
                )

            if (arrowPosition == GotItBalloonPosition.BELOW) {
                // Arrow at top, pointing up
                arrowPath.moveTo(center - arrowWidthPx / 2, rectTop)
                arrowPath.lineTo(center, 0f)
                arrowPath.lineTo(center + arrowWidthPx / 2, rectTop)
            } else {
                // ABOVE: arrow at bottom, pointing down
                arrowPath.moveTo(center - arrowWidthPx / 2, rectBottom)
                arrowPath.lineTo(center, size.height)
                arrowPath.lineTo(center + arrowWidthPx / 2, rectBottom)
            }
        }

        GotItBalloonPosition.START,
        GotItBalloonPosition.END -> {
            val center =
                arrowOffsetPx.coerceIn(
                    rectTop + cornerRadiusPx + arrowWidthPx / 2,
                    rectBottom - cornerRadiusPx - arrowWidthPx / 2,
                )

            if (arrowPosition == GotItBalloonPosition.END) {
                // Arrow at start/left, pointing left
                arrowPath.moveTo(rectLeft, center - arrowWidthPx / 2)
                arrowPath.lineTo(0f, center)
                arrowPath.lineTo(rectLeft, center + arrowWidthPx / 2)
            } else {
                // START: arrow at end/right, pointing right
                arrowPath.moveTo(rectRight, center - arrowWidthPx / 2)
                arrowPath.lineTo(size.width, center)
                arrowPath.lineTo(rectRight, center + arrowWidthPx / 2)
            }
        }
    }
    arrowPath.close()

    val finalPath = Path().apply { op(baseRectPath, arrowPath, PathOperation.Union) }

    return Outline.Generic(finalPath)
}

/**
 * Creates a [java.awt.Shape] for a balloon with a directional arrow, mirroring the geometry of [createBalloonOutline]
 * exactly but producing an AWT shape suitable for use with [java.awt.Window.setShape].
 *
 * All coordinate parameters are in **logical units** (equivalent to dp values as ints), matching the AWT coordinate
 * system where `1 logical unit == 1 dp`.
 *
 * The arrow is drawn on the side facing the anchor — the opposite side from [arrowPosition]:
 * - [GotItBalloonPosition.BELOW] → arrow at the top (pointing up)
 * - [GotItBalloonPosition.ABOVE] → arrow at the bottom (pointing down)
 * - [GotItBalloonPosition.START] → arrow at the end (pointing right/end)
 * - [GotItBalloonPosition.END] → arrow at the start (pointing left/start)
 */
@InternalJewelApi
@ApiStatus.Internal
public fun createBalloonAwtShape(
    size: IntSize,
    arrowWidthPx: Int,
    arrowHeightPx: Int,
    cornerRadiusPx: Int,
    arrowPosition: GotItBalloonPosition,
    arrowOffsetPx: Int,
    layoutDirection: LayoutDirection,
): Shape {
    val isRtl = layoutDirection == LayoutDirection.Rtl

    val rectLeft = if (arrowPosition == GotItBalloonPosition.END) arrowHeightPx.toDouble() else 0.0
    val rectTop = if (arrowPosition == GotItBalloonPosition.BELOW) arrowHeightPx.toDouble() else 0.0
    val rectRight =
        size.width.toDouble() - if (arrowPosition == GotItBalloonPosition.START) arrowHeightPx.toDouble() else 0.0
    val rectBottom =
        size.height.toDouble() - if (arrowPosition == GotItBalloonPosition.ABOVE) arrowHeightPx.toDouble() else 0.0

    val arcSize = cornerRadiusPx.toDouble() * 2
    val roundRect =
        RoundRectangle2D.Double(rectLeft, rectTop, rectRight - rectLeft, rectBottom - rectTop, arcSize, arcSize)

    val arrowPath = Path2D.Double()

    when (arrowPosition) {
        GotItBalloonPosition.ABOVE,
        GotItBalloonPosition.BELOW -> {
            val actualOffsetPx = if (isRtl) size.width - arrowOffsetPx else arrowOffsetPx
            val center =
                actualOffsetPx
                    .toDouble()
                    .coerceIn(
                        rectLeft + cornerRadiusPx + arrowWidthPx / 2.0,
                        rectRight - cornerRadiusPx - arrowWidthPx / 2.0,
                    )

            if (arrowPosition == GotItBalloonPosition.BELOW) {
                // Arrow at top, pointing up
                arrowPath.moveTo(center - arrowWidthPx / 2.0, rectTop)
                arrowPath.lineTo(center, 0.0)
                arrowPath.lineTo(center + arrowWidthPx / 2.0, rectTop)
            } else {
                // ABOVE: arrow at bottom, pointing down
                arrowPath.moveTo(center - arrowWidthPx / 2.0, rectBottom)
                arrowPath.lineTo(center, size.height.toDouble())
                arrowPath.lineTo(center + arrowWidthPx / 2.0, rectBottom)
            }
        }

        GotItBalloonPosition.START,
        GotItBalloonPosition.END -> {
            val center =
                arrowOffsetPx
                    .toDouble()
                    .coerceIn(
                        rectTop + cornerRadiusPx + arrowWidthPx / 2.0,
                        rectBottom - cornerRadiusPx - arrowWidthPx / 2.0,
                    )

            if (arrowPosition == GotItBalloonPosition.END) {
                // Arrow at start/left, pointing left
                arrowPath.moveTo(rectLeft, center - arrowWidthPx / 2.0)
                arrowPath.lineTo(0.0, center)
                arrowPath.lineTo(rectLeft, center + arrowWidthPx / 2.0)
            } else {
                // START: arrow at end/right, pointing right
                arrowPath.moveTo(rectRight, center - arrowWidthPx / 2.0)
                arrowPath.lineTo(size.width.toDouble(), center)
                arrowPath.lineTo(rectRight, center + arrowWidthPx / 2.0)
            }
        }
    }
    arrowPath.closePath()

    return Area(roundRect).also { it.add(Area(arrowPath)) }
}
