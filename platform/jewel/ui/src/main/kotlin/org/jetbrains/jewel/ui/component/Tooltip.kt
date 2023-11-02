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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle
import org.jetbrains.jewel.ui.util.isDark

@Composable
public fun Tooltip(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    style: TooltipStyle = JewelTheme.tooltipStyle,
    tooltipPlacement: TooltipPlacement = TooltipPlacement(
        contentOffset = style.metrics.tooltipOffset,
        alignment = style.metrics.tooltipAlignment,
        density = LocalDensity.current,
    ),
    content: @Composable () -> Unit,
) {
    TooltipArea(
        tooltip = {
            CompositionLocalProvider(
                LocalContentColor provides style.colors.content,
            ) {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = style.metrics.shadowSize,
                            shape = RoundedCornerShape(style.metrics.cornerSize),
                            ambientColor = style.colors.shadow,
                            spotColor = Color.Transparent,
                        )
                        .background(
                            color = style.colors.background,
                            shape = RoundedCornerShape(style.metrics.cornerSize),
                        )
                        .border(
                            width = style.metrics.borderWidth,
                            color = style.colors.border,
                            shape = RoundedCornerShape(style.metrics.cornerSize),
                        )
                        .padding(style.metrics.contentPadding),
                ) {
                    OverrideDarkMode(style.colors.background.isDark()) {
                        tooltip()
                    }
                }
            }
        },
        modifier = modifier,
        delayMillis = style.metrics.showDelay.inWholeMilliseconds.toInt(),
        tooltipPlacement = tooltipPlacement,
        content = content,
    )
}

public class TooltipPlacement(
    private val contentOffset: DpOffset,
    private val alignment: Alignment.Horizontal,
    private val density: Density,
    private val windowMargin: Dp = 4.dp,
) : TooltipPlacement {

    @Composable
    @Suppress("OVERRIDE_DEPRECATION")
    override fun positionProvider(): PopupPositionProvider {
        error("Not supported")
    }

    @Composable
    override fun positionProvider(cursorPosition: Offset): PopupPositionProvider =
        rememberTooltipPositionProvider(
            cursorPosition = cursorPosition,
            contentOffset = contentOffset,
            alignment = alignment,
            density = density,
            windowMargin = windowMargin,
        )
}

@Composable
private fun rememberTooltipPositionProvider(
    cursorPosition: Offset,
    contentOffset: DpOffset,
    alignment: Alignment.Horizontal,
    density: Density,
    windowMargin: Dp = 4.dp,
) =
    remember(contentOffset, alignment, density, windowMargin) {
        TooltipPositionProvider(
            cursorPosition = cursorPosition,
            contentOffset = contentOffset,
            alignment = alignment,
            density = density,
            windowMargin = windowMargin,
        )
    }

private class TooltipPositionProvider(
    private val cursorPosition: Offset,
    private val contentOffset: DpOffset,
    private val alignment: Alignment.Horizontal,
    private val density: Density,
    private val windowMargin: Dp = 4.dp,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        with(density) {
            val windowSpaceBounds = IntRect(
                left = windowMargin.roundToPx(),
                top = windowMargin.roundToPx(),
                right = windowSize.width - windowMargin.roundToPx(),
                bottom = windowSize.height - windowMargin.roundToPx(),
            )

            val contentOffsetX = contentOffset.x.roundToPx()
            val contentOffsetY = contentOffset.y.roundToPx()

            val posX = cursorPosition.x.toInt() + anchorBounds.left
            val posY = cursorPosition.y.toInt() + anchorBounds.top

            val x = posX + alignment.align(popupContentSize.width, 0, layoutDirection) + contentOffsetX

            val aboveSpacing = cursorPosition.y - contentOffsetY - windowSpaceBounds.top
            val belowSpacing = windowSpaceBounds.bottom - cursorPosition.y - contentOffsetY

            val y =
                if (belowSpacing > popupContentSize.height || belowSpacing >= aboveSpacing) {
                    posY + contentOffsetY
                } else {
                    posY - contentOffsetY - popupContentSize.height
                }

            val popupBounds =
                IntRect(x, y, x + popupContentSize.width, y + popupContentSize.height)
                    .constrainedIn(windowSpaceBounds)

            IntOffset(popupBounds.left, popupBounds.top)
        }
}
