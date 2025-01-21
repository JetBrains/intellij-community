package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupPositionProviderAtPosition
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle
import org.jetbrains.jewel.ui.util.isDark

/**
 * Shows a tooltip when the mouse pointer lingers on the [content] for long enough. Provides the styling for the tooltip
 * container.
 *
 * @param tooltip The content of the tooltip.
 * @param modifier Modifier to apply to the content's wrapper
 * @param enabled When true, the tooltip can be shown. When false, it will never show.
 * @param style The style to apply to the tooltip.
 * @param tooltipPlacement The placement of the tooltip.
 * @param content The component for which to show the tooltip on hover.
 */
@Composable
public fun Tooltip(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipPlacement: TooltipPlacement = style.metrics.placement,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = { if (enabled) TooltipImpl(style, tooltip) else Box {} },
        modifier = modifier,
        delayMillis = style.metrics.showDelay.inWholeMilliseconds.toInt(),
        tooltipPlacement = tooltipPlacement,
        content = content,
    )
}

@Composable
private fun TooltipImpl(style: TooltipStyle, tooltip: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides style.colors.content,
        LocalTextStyle provides LocalTextStyle.current.copy(color = style.colors.content),
    ) {
        Box(
            modifier =
                Modifier.shadow(
                        elevation = style.metrics.shadowSize,
                        shape = RoundedCornerShape(style.metrics.cornerSize),
                        ambientColor = style.colors.shadow,
                        spotColor = Color.Transparent,
                    )
                    .background(color = style.colors.background, shape = RoundedCornerShape(style.metrics.cornerSize))
                    .border(
                        width = style.metrics.borderWidth,
                        color = style.colors.border,
                        shape = RoundedCornerShape(style.metrics.cornerSize),
                    )
                    .padding(style.metrics.contentPadding)
        ) {
            OverrideDarkMode(style.colors.background.isDark()) { tooltip() }
        }
    }
}

/**
 * [TooltipPlacement] implementation for providing a [PopupPositionProvider] that calculates the position of the popup
 * relative to the current mouse cursor position, but never changes it after showing the popup.
 *
 * @param offset [DpOffset] to be added to the position of the popup.
 * @param alignment The alignment of the popup relative to the current cursor position.
 * @param windowMargin Defines the area within the window that limits the placement of the popup.
 */
@ExperimentalJewelApi
public class FixedCursorPoint(
    private val offset: DpOffset = DpOffset.Zero,
    private val alignment: Alignment = Alignment.BottomEnd,
    private val windowMargin: Dp = 4.dp,
) : TooltipPlacement {
    @Composable
    override fun positionProvider(cursorPosition: Offset): PopupPositionProvider =
        rememberPopupPositionProviderAtFixedPosition(
            positionPx = cursorPosition,
            offset = offset,
            alignment = alignment,
            windowMargin = windowMargin,
        )
}

/**
 * A [PopupPositionProvider] that positions the popup at the given position relative to the anchor, but never updates it
 * after showing the popup.
 *
 * @param positionPx the offset, in pixels, relative to the anchor, to position the popup at.
 * @param offset [DpOffset] to be added to the position of the popup.
 * @param alignment The alignment of the popup relative to desired position.
 * @param windowMargin Defines the area within the window that limits the placement of the popup.
 */
@ExperimentalJewelApi
@Composable
public fun rememberPopupPositionProviderAtFixedPosition(
    positionPx: Offset,
    offset: DpOffset = DpOffset.Zero,
    alignment: Alignment = Alignment.BottomEnd,
    windowMargin: Dp = 4.dp,
): PopupPositionProvider =
    with(LocalDensity.current) {
        val offsetPx = Offset(offset.x.toPx(), offset.y.toPx())
        val windowMarginPx = windowMargin.roundToPx()

        val initialPosition = remember { positionPx }

        remember(offsetPx, alignment, windowMarginPx) {
            PopupPositionProviderAtPosition(
                positionPx = initialPosition,
                isRelativeToAnchor = true,
                offsetPx = offsetPx,
                alignment = alignment,
                windowMarginPx = windowMarginPx,
            )
        }
    }
