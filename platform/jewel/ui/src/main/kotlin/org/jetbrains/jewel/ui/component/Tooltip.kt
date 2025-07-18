package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.component.styling.TooltipAutoHideBehavior
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
 * @param autoHideBehavior The delay before the tooltip is automatically hidden. Jewel offers three options, matching
 *   Swing behavior. [AutoHideBehavior.Normal] is a shorter delay (10 seconds) to be used when the tooltip text is a
 *   single line. [AutoHideBehavior.Long] is a longer delay (30 seconds) to be used when the tooltip text is multi-line.
 *   [AutoHideBehavior.Never] to never hide the tooltip. [AutoHideBehavior.Normal] and [AutoHideBehavior.Long] durations
 *   can be altered using [org.jetbrains.jewel.ui.component.styling.TooltipMetrics].
 * @param content The component for which to show the tooltip on hover
 * @see com.intellij.ide.HelpTooltip
 */
@Deprecated("Please, use the overload without the [AutoHideBehavior].")
@Composable
public fun Tooltip(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipPlacement: TooltipPlacement = style.metrics.placement,
    autoHideBehavior: AutoHideBehavior = AutoHideBehavior.Normal,
    content: @Composable () -> Unit,
) {
    FlagAwareTooltipArea(
        tooltip = {
            TooltipImpl(
                enabled = enabled,
                style = style,
                autoHideBehavior =
                    when (autoHideBehavior) {
                        AutoHideBehavior.Normal -> TooltipAutoHideBehavior.Normal
                        AutoHideBehavior.Long -> TooltipAutoHideBehavior.Long
                        AutoHideBehavior.Never -> TooltipAutoHideBehavior.Never
                    },
                tooltip = tooltip,
            )
        },
        modifier = modifier,
        delayMillis = style.metrics.showDelay.inWholeMilliseconds.toInt(),
        tooltipPlacement = tooltipPlacement,
        content = content,
    )
}

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
    FlagAwareTooltipArea(
        tooltip = { TooltipImpl(enabled, style, tooltip = tooltip) },
        modifier = modifier,
        delayMillis = style.metrics.showDelay.inWholeMilliseconds.toInt(),
        tooltipPlacement = tooltipPlacement,
        content = content,
    )
}

@Composable
private fun TooltipImpl(
    enabled: Boolean,
    style: TooltipStyle,
    autoHideBehavior: TooltipAutoHideBehavior = style.autoHideBehavior,
    tooltip: @Composable () -> Unit,
) {
    var isVisible by remember { mutableStateOf(true) }
    if (!enabled) return

    LaunchedEffect(style.autoHideBehavior) {
        when (style.autoHideBehavior) {
            TooltipAutoHideBehavior.Normal -> {
                delay(style.metrics.regularDisappearDelay)
                isVisible = false
            }

            TooltipAutoHideBehavior.Long -> {
                delay(style.metrics.fullDisappearDelay)
                isVisible = false
            }

            TooltipAutoHideBehavior.Never -> {}
        }
    }

    if (!isVisible) return

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
@ApiStatus.Experimental
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
@ApiStatus.Experimental
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

// TODO: When removing it, please replace usages with [TooltipAutoHideBehavior]
@Deprecated(
    "Replace with TooltipAutoHideBehavior",
    replaceWith =
        ReplaceWith("TooltipAutoHideBehavior", "org.jetbrains.jewel.ui.component.styling.TooltipAutoHideBehavior"),
    level = DeprecationLevel.WARNING,
)
public enum class AutoHideBehavior {
    Never,
    Normal,
    Long,
}
