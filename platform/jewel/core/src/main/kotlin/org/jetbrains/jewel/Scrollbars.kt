package org.jetbrains.jewel

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

@Composable
fun VerticalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: ScrollThumbDefaults = IntelliJTheme.scrollThumbDefaults,
    colors: ScrollThumbColors = defaults.colors()
) =
    CompositionLocalProvider(
        LocalScrollbarStyle provides ScrollbarStyle(
            minimalHeight = defaults.minHeight(),
            thickness = defaults.thickness(),
            shape = defaults.shape(),
            hoverDurationMillis = defaults.hoverDurationMillis(),
            unhoverColor = colors.unHoverColor(),
            hoverColor = colors.hoverColor()
        )
    ) {
        VerticalScrollbar(
            adapter = adapter,
            modifier = modifier,
            reverseLayout = reverseLayout,
            style = LocalScrollbarStyle.current,
            interactionSource = interactionSource
        )
    }

@Composable
fun HorizontalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: ScrollThumbDefaults = IntelliJTheme.scrollThumbDefaults,
    colors: ScrollThumbColors = defaults.colors()
) = CompositionLocalProvider(
    LocalScrollbarStyle provides ScrollbarStyle(
        defaults.minHeight(),
        defaults.thickness(),
        defaults.shape(),
        defaults.hoverDurationMillis(),
        colors.unHoverColor(),
        colors.hoverColor()
    )
) {
    HorizontalScrollbar(
        adapter = adapter,
        modifier = modifier,
        reverseLayout = reverseLayout,
        style = LocalScrollbarStyle.current,
        interactionSource = interactionSource
    )
}

@Stable
interface ScrollThumbDefaults {

    @Composable
    fun shape(): Shape

    @Composable
    fun minHeight(): Dp

    @Composable
    fun thickness(): Dp

    @Composable
    fun hoverDurationMillis(): Int

    @Composable
    fun colors(): ScrollThumbColors
}

@Stable
interface ScrollThumbColors {
    @Composable
    fun unHoverColor(): Color

    @Composable
    fun hoverColor(): Color
}

fun scrollThumbColors(
    unHoverColor: Color,
    hoverColor: Color
): ScrollThumbColors = DefaultScrollThumbColors(unHoverColor, hoverColor)

@Immutable
private data class DefaultScrollThumbColors(
    private val unHoverColor: Color,
    private val hoverColor: Color
) : ScrollThumbColors {
    @Composable
    override fun unHoverColor(): Color = unHoverColor

    @Composable
    override fun hoverColor(): Color = hoverColor
}

internal val LocalScrollThumbDefaults = staticCompositionLocalOf<ScrollThumbDefaults> {
    error("No ScrollThumbDefaults provided")
}
