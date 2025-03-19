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
 * A tooltip component that follows the standard visual styling.
 *
 * Provides a floating tooltip that appears when hovering over or focusing the target content. The tooltip follows
 * platform conventions for timing, positioning, and appearance, supporting both mouse and keyboard navigation.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/tooltip.html)
 *
 * **Usage example:**
 * [`Tooltips.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Tooltips.kt)
 *
 * **Swing equivalent:**
 * [`HelpTooltip`](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ide/HelpTooltip.java)
 * and [How to Use Tool Tips](https://docs.oracle.com/javase/tutorial/uiswing/components/tooltip.html)
 *
 * @param tooltip The content to be displayed in the tooltip
 * @param modifier Modifier to apply to the content's wrapper
 * @param enabled Controls whether the tooltip can be shown. When false, it will never show
 * @param style The visual styling configuration for the tooltip
 * @param tooltipPlacement The placement strategy for positioning the tooltip relative to the content
 * @param content The component for which to show the tooltip on hover
 * @see com.intellij.ide.HelpTooltip
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
 * A tooltip placement strategy that positions the tooltip relative to the cursor position.
 *
 * Provides a [PopupPositionProvider] that calculates the position of the tooltip relative to the current mouse cursor
 * position, maintaining that position after showing the tooltip. This allows for stable tooltip positioning that
 * doesn't follow cursor movement.
 *
 * @param offset Additional offset to be added to the tooltip position
 * @param alignment The alignment of the tooltip relative to the cursor position
 * @param windowMargin The minimum distance to maintain from window edges
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
 * Creates a position provider that maintains a fixed position relative to an anchor point.
 *
 * Returns a [PopupPositionProvider] that positions the popup at the given position relative to the anchor, maintaining
 * that position after showing the popup. This ensures stable positioning that doesn't update with subsequent anchor
 * movement.
 *
 * @param positionPx The offset in pixels relative to the anchor
 * @param offset Additional offset to be added to the popup position
 * @param alignment The alignment of the popup relative to the desired position
 * @param windowMargin The minimum distance to maintain from window edges
 * @return A [PopupPositionProvider] that maintains the specified positioning
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
