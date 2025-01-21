package org.jetbrains.jewel.foundation

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect

internal fun RoundRect.grow(delta: Float) =
    RoundRect(
        left = left - delta,
        top = top - delta,
        right = right + delta,
        bottom = bottom + delta,
        topLeftCornerRadius = CornerRadius(topLeftCornerRadius.x + delta, topLeftCornerRadius.y + delta),
        topRightCornerRadius = CornerRadius(topRightCornerRadius.x + delta, topRightCornerRadius.y + delta),
        bottomLeftCornerRadius = CornerRadius(bottomLeftCornerRadius.x + delta, bottomLeftCornerRadius.y + delta),
        bottomRightCornerRadius = CornerRadius(bottomRightCornerRadius.x + delta, bottomRightCornerRadius.y + delta),
    )

internal fun RoundRect.shrink(delta: Float) =
    RoundRect(
        left = left + delta,
        top = top + delta,
        right = right - delta,
        bottom = bottom - delta,
        topLeftCornerRadius = CornerRadius(topLeftCornerRadius.x - delta, topLeftCornerRadius.y - delta),
        topRightCornerRadius = CornerRadius(topRightCornerRadius.x - delta, topRightCornerRadius.y - delta),
        bottomLeftCornerRadius = CornerRadius(bottomLeftCornerRadius.x - delta, bottomLeftCornerRadius.y - delta),
        bottomRightCornerRadius = CornerRadius(bottomRightCornerRadius.x - delta, bottomRightCornerRadius.y - delta),
    )

internal fun RoundRect.hasAtLeastOneNonRoundedCorner() =
    topLeftCornerRadius.x == 0f && topLeftCornerRadius.y == 0f ||
        topRightCornerRadius.x == 0f && topRightCornerRadius.y == 0f ||
        bottomLeftCornerRadius.x == 0f && bottomLeftCornerRadius.y == 0f ||
        bottomRightCornerRadius.x == 0f && bottomRightCornerRadius.y == 0f
